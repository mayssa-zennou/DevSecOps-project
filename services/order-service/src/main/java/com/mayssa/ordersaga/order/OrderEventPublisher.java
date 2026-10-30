package com.mayssa.ordersaga.order;

import tools.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderEventPublisher {

    private static final String TOPIC = "order.created";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper mapper;

    public OrderEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper mapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.mapper = mapper;
    }

    public void publishOrderCreated(OrderCreatedEvent event) {
        try {
            String json = mapper.writeValueAsString(event);
            kafkaTemplate.send(TOPIC, event.eventId(), json);
        } catch (Exception e) {
            throw new RuntimeException("Impossible de publier l'événement", e);
        }
    }
}