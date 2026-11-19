package com.mayssa.ordersaga.inventory.listener;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mayssa.ordersaga.inventory.InventoryService;
import com.mayssa.ordersaga.inventory.ReservationResult;
import com.mayssa.ordersaga.inventory.events.OrderCreatedEvent;
import com.mayssa.ordersaga.inventory.events.StockRejectedEvent;
import com.mayssa.ordersaga.inventory.events.StockReservedEvent;

import tools.jackson.databind.ObjectMapper;

@Component
public class OrderCreatedListener {

    private static final Logger log= LoggerFactory.getLogger(OrderCreatedListener.class);

    private final InventoryService service;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public OrderCreatedListener(
            InventoryService service,
            JdbcTemplate jdbc,
            ObjectMapper mapper) {
        this.service = service;
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @KafkaListener(topics = "order.created")
    @Transactional
    public void onOrderCreated(String payload) {
        OrderCreatedEvent event = mapper.readValue(payload, OrderCreatedEvent.class);

        int inserted = jdbc.update(
            "INSERT INTO processed_events(event_id) VALUES (?) ON CONFLICT DO NOTHING",
            event.eventId());
        if (inserted == 0) {
            log.info("Event {} already processed, ignored", event.eventId());
            return;
        }

        var result = service.reserve(event.productId(), event.quantity());
        if (result == ReservationResult.RESERVED) {
            enqueue("stock.reserved", event.orderId(),
                    new StockReservedEvent(eventId("stock.reserved", event.orderId()), event.orderId(), Instant.now()));
        } else {
            enqueue("stock.rejected", event.orderId(),
                    new StockRejectedEvent(eventId("stock.rejected", event.orderId()), event.orderId(),
                                        result.name(), Instant.now()));
        }
    }

    private void enqueue(String topic, String key, Object event) {
        jdbc.update("INSERT INTO outbox(topic, msg_key, payload) VALUES (?, ?, ?)",
                topic, key, mapper.writeValueAsString(event));
    }

    private static String eventId(String topic, String orderId) {
        return UUID.nameUUIDFromBytes((topic + ":" + orderId).getBytes(StandardCharsets.UTF_8)).toString();
    }

}
