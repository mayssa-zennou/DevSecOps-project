package com.mayssa.ordersaga.notification_service.events;

import java.time.Instant;

public record OrderRejectedEvent(
    String eventId, String orderId, String userId, String userEmail, 
    String reason, Instant occurredAt
) {}