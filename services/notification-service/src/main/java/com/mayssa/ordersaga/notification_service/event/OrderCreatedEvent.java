package com.mayssa.ordersaga.notification_service.event;

import java.time.Instant;

public record OrderCreatedEvent(
        String eventId,
        String orderId,
        String userId,
        String userEmail,
        Instant occurredAt) {}
