package com.mayssa.ordersaga.order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;


@RestController 
@RequestMapping("/orders")
public class OrderController {
    
    // input contract: what the client can send
    public record CreateOrderRequest(@NotBlank String productId, @Min(1) int quantity) {}
    
    // output contract: what we choose to expose 
    public record OrderResponse(UUID id, String productId, int quantity, OrderStatus status) {
        static OrderResponse from(Order order) {
            return new OrderResponse(order.getId(), order.getProductId(), order.getQuantity(), order.getStatus());
        }   
    }
    private final OrderRepository repository;
    private final OrderEventPublisher publisher;

    public OrderController(OrderRepository repository,OrderEventPublisher publisher) {
        this.repository = repository;
        this.publisher=publisher;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse placeOrder(@Valid @RequestBody CreateOrderRequest request) {
        Order order = repository.save(new Order(request.productId(), request.quantity()));
        publisher.publishOrderCreated(new OrderCreatedEvent(
                UUID.randomUUID().toString(),
                order.getId().toString(),
                "alice",               // provisoire
                "alice@test.com",      // provisoire
                Instant.now()));
        return OrderResponse.from(order);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id) {
        return repository.findById(id)
            .map(order -> ResponseEntity.ok(OrderResponse.from(order)))
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<OrderResponse> listOrders() {
        return repository.findAll().stream()
            .map(OrderResponse::from)
            .toList();
    }

}