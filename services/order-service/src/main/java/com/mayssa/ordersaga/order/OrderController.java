package com.mayssa.ordersaga.order;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.mayssa.ordersaga.order.events.OrderCreatedEvent;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
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
    @Transactional(rollbackFor = Exception.class)
    public OrderResponse placeOrder(@Valid @RequestBody CreateOrderRequest request, @AuthenticationPrincipal Jwt jwt) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access token must include an email claim");
        }
        Order order = repository.save(new Order(request.productId(), request.quantity(), jwt.getSubject(), email));
        OrderCreatedEvent event = new OrderCreatedEvent(
                UUID.randomUUID().toString(),
                order.getId().toString(),
                order.getUserId(),
                order.getUserEmail(),
                order.getProductId(), 
                order.getQuantity(),
                Instant.now());
        publisher.publish("order.created", event.orderId(), event);
        return OrderResponse.from(order);
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, Authentication auth) {
        return (isAdmin(auth) ? repository.findById(id) : repository.findByIdAndUserId(id, jwt.getSubject()))
            .map(order -> ResponseEntity.ok(OrderResponse.from(order)))
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public List<OrderResponse> listOrders(@AuthenticationPrincipal Jwt jwt, Authentication auth) {
        return (isAdmin(auth) ? repository.findAll() : repository.findByUserId(jwt.getSubject())).stream()
            .map(OrderResponse::from)
            .toList();
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_admin"));
    }
}
