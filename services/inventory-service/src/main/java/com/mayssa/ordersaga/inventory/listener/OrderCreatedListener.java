package com.mayssa.ordersaga.inventory.listener;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
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
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;

    public OrderCreatedListener(
            InventoryService service,
            JdbcTemplate jdbc,
            KafkaTemplate<String, String> kafka,
            ObjectMapper mapper) {
        this.service = service;
        this.jdbc = jdbc;
        this.kafka = kafka;
        this.mapper = mapper;
    }

    @KafkaListener(topics = "order.created", groupId = "inventory-service")
    @Transactional(rollbackFor = Exception.class)
    public void onOrderCreated(String payload) throws Exception {
        OrderCreatedEvent event = mapper.readValue(payload, OrderCreatedEvent.class);

        int inserted = jdbc.update(
            "INSERT INTO processed_events(event_id) VALUES (?) ON CONFLICT DO NOTHING",
            event.eventId());
        if (inserted == 0) {
            log.info("Event {} is already treated and will be ignored", event.eventId());
            return;
        }

        var result = service.reserve(event.productId(), event.quantity());
        if (result == ReservationResult.RESERVED) {
            send("stock.reserved", event.orderId(),
                 new StockReservedEvent(UUID.randomUUID().toString(), event.orderId(), Instant.now()));
        } else {
            send("stock.rejected", event.orderId(),
                 new StockRejectedEvent(UUID.randomUUID().toString(), event.orderId(),
                                        result.name(), Instant.now()));
        }
    }

    private void send(String topic, String key, Object event) throws Exception {
        try {
            kafka.send(topic, key, mapper.writeValueAsString(event)).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw exception;
        }
    }
}