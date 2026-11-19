package com.mayssa.ordersaga.order.events;

import java.time.Instant;

public record OrderCreatedEvent(
        String eventId, String orderId, String userId, String userEmail,
        String productId, int quantity, Instant occurredAt
) {}