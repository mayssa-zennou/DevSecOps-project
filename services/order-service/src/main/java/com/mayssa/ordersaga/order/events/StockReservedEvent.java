package com.mayssa.ordersaga.order.events;

import java.time.Instant;

public record StockReservedEvent(
    String eventId, String orderId, 
    Instant occurredAt
) {}
