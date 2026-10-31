package com.mayssa.ordersaga.notification_service.event;

import java.time.Instant;

public record StockLowEvent(
        String eventId,
        String productId,
        String productName,
        String remaining,
        Instant occurredAt) {
}
