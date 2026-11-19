package com.mayssa.ordersaga.inventory.events;

import java.time.Instant;

public record StockRejectedEvent(
    String eventId, String orderId, 
    String reason, Instant occurredAt
) {}
