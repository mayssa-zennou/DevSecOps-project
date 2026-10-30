package com.mayssa.ordersaga.order;

import java.time.Instant;

public record OrderCreatedEvent(
        String eventId,
        String orderId,
        String userId,
        String userEmail,
        Instant occurredAt) {}