package com.mayssa.ordersaga.inventory.events;

import java.time.Instant;

public record StockReservedEvent(
    String eventId, String orderId, 
    Instant occurredAt
) {}
