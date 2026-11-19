package com.mayssa.ordersaga.order.listener;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.mayssa.ordersaga.order.Order;
import com.mayssa.ordersaga.order.OrderEventPublisher;
import com.mayssa.ordersaga.order.OrderRepository;
import com.mayssa.ordersaga.order.OrderStatus;
import com.mayssa.ordersaga.order.events.OrderConfirmedEvent;
import com.mayssa.ordersaga.order.events.OrderRejectedEvent;
import com.mayssa.ordersaga.order.events.StockRejectedEvent;
import com.mayssa.ordersaga.order.events.StockReservedEvent;

import tools.jackson.databind.ObjectMapper;

@Component
public class StockResultListener {
    
    private static final Logger log = LoggerFactory.getLogger(StockResultListener.class);
    
    private final OrderRepository repository;
    private final ObjectMapper mapper;
    private final OrderEventPublisher publisher;

    public StockResultListener(OrderRepository repository, ObjectMapper mapper, OrderEventPublisher publisher) {
        this.repository = repository;
        this.mapper = mapper;
        this.publisher = publisher;
    }
    
    @KafkaListener(topics = "stock.reserved")
    @Transactional(rollbackFor = Exception.class)    
    public void onStockReserved(String payload) throws Exception {
        StockReservedEvent event = mapper.readValue(payload, StockReservedEvent.class);
        Order order = pendingOrder(event.orderId());
        if (order == null) {
            return;
        }
        order.confirm();
        publisher.publish("order.confirmed", event.orderId(),
                new OrderConfirmedEvent(eventId("order.confirmed", order.getId().toString()), event.orderId(),
                        order.getUserId(), order.getUserEmail(), Instant.now()));    
    }

    @KafkaListener(topics = "stock.rejected")
    @Transactional(rollbackFor = Exception.class)
    public void onStockRejected(String payload) throws Exception {
        StockRejectedEvent event = mapper.readValue(payload, StockRejectedEvent.class);
        Order order = pendingOrder(event.orderId());
        if (order == null) {
            return;
        }
        order.reject();
        publisher.publish("order.rejected", event.orderId(),
                new OrderRejectedEvent(eventId("order.rejected", order.getId().toString()), event.orderId(),
                        order.getUserId(), order.getUserEmail(), event.reason(), Instant.now()));
    }    

    // Returns the order only if it exists and is still PENDING; otherwise the event is an orphan or a replay.
    private Order pendingOrder(String orderId) {
        Order order = repository.findById(UUID.fromString(orderId)).orElse(null);
        if (order == null) {
            log.warn("Can't find order {}, event ignored", orderId);
            return null;
        }
        if (order.getStatus()!=OrderStatus.PENDING) {
            log.info("Order {} already {}, event ignored", orderId, order.getStatus());
            return null;
        }
        return order;
    }

    private static String eventId(String topic, String orderId) {
        return UUID.nameUUIDFromBytes((topic + ":" + orderId).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
