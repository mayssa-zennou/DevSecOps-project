package com.mayssa.ordersaga.inventory;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;


@RestController 
@RequestMapping("/stock/{productId}")
public class StockController {
    
    // the product comes from the URL, only the quantity is in the body
    public record ReserveRequest(@Min(1) int quantity) {}

    public record StockResponse(String productId, int available) {}

    private final InventoryService service;

    public StockController(InventoryService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<StockResponse> get(@PathVariable String productId) {
        return service.getStock(productId)
            .map(s -> ResponseEntity.ok(new StockResponse(s.getProductId(), s.getAvailable())))
            .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping("/reservations")
    public ResponseEntity<Void> reserve(@PathVariable String productId, @Valid @RequestBody ReserveRequest request) {
        return switch (service.reserve(productId, request.quantity())) {
            case RESERVED -> ResponseEntity.ok().build();
            case INSUFFICIENT_STOCK -> ResponseEntity.status(HttpStatus.CONFLICT).build();
            case UNKNOWN_PRODUCT -> ResponseEntity.notFound().build();
        };
    }
    
}
