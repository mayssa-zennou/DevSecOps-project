package com.mayssa.ordersaga.notification_service.consumer;


import com.mayssa.ordersaga.notification_service.events.OrderConfirmedEvent;
import com.mayssa.ordersaga.notification_service.events.OrderRejectedEvent;
import com.mayssa.ordersaga.notification_service.service.NotificationService;
import lombok.AllArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@AllArgsConstructor
@Component
public class NotificationConsumer {
    private final NotificationService service;
    private final ObjectMapper mapper;

    @KafkaListener(topics = "order.confirmed")
    public void onOrderConfirmed(String json) throws Exception {
        service.handleOrderConfirmed(mapper.readValue(json, OrderConfirmedEvent.class));
    }

    @KafkaListener(topics = "order.rejected")
    public void onOrderRejected(String json) throws Exception {
        service.handleOrderRejected(mapper.readValue(json, OrderRejectedEvent.class));
    }

}
