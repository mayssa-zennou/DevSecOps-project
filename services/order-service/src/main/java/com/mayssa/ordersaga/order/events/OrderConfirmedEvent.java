package com.mayssa.ordersaga.order.events;

import java.time.Instant;

public record OrderConfirmedEvent(
    String eventId, String orderId, String userId, String userEmail, 
    Instant occurredAt
) {}

