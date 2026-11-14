package com.mayssa.ordersaga.order.events;

import java.time.Instant;

public record StockRejectedEvent(
    String eventId, String orderId, 
    String reason, Instant occurredAt
) {}
