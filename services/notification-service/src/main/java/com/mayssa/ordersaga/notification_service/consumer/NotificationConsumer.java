package com.mayssa.ordersaga.notification_service.consumer;


import com.mayssa.ordersaga.notification_service.event.OrderCreatedEvent;
import com.mayssa.ordersaga.notification_service.event.StockLowEvent;
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

    @KafkaListener(topics = "order.created")
    public void onOrderCreated(String json) throws Exception{
        service.handleOrderCreated(mapper.readValue(json, OrderCreatedEvent.class));
    }

    @KafkaListener(topics = "stock.low")
    public void onStockLow(String json) throws  Exception{
        service.handleStockLow(mapper.readValue(json, StockLowEvent.class));
    }


}
