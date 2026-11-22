package com.mayssa.ordersaga.order;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findByUserId(String userId);

    Optional<Order> findByIdAndUserId(UUID id, String userId);
} 