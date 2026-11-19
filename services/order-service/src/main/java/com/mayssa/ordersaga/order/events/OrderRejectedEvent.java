package com.mayssa.ordersaga.order.events;

import java.time.Instant;

public record OrderRejectedEvent(
    String eventId, String orderId, String userId, String userEmail, 
    String reason, Instant occurredAt
) {}