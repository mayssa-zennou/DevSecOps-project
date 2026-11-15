package com.mayssa.ordersaga.order;

import java.util.concurrent.TimeUnit;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

@Component
public class OrderEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper mapper;
    
    public OrderEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper mapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.mapper = mapper;
    }

    public void publish(String topic, String orderId, Object event) throws Exception {
        try {
            kafkaTemplate.send(topic, orderId, mapper.writeValueAsString(event)).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        }

    }

}