package com.mayssa.ordersaga.order;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA, invisible for the rest of the code
public class Order {

    @Id
    private UUID id;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public Order(String productId, int quantity) {
        this.id = UUID.randomUUID();
        this.productId = productId;
        this.quantity = quantity;
        this.status = OrderStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public void confirm() {
        requirePending();
        this.status = OrderStatus.CONFIRMED;
    }

    public void reject() {
        requirePending();
        this.status = OrderStatus.REJECTED;
    }

    private void requirePending() {
        if (status != OrderStatus.PENDING) {
            throw new IllegalStateException("Order " + id + " is already " + status);
        }
    }
}