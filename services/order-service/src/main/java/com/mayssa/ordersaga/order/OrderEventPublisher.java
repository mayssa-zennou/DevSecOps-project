package com.mayssa.ordersaga.order;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

@Component
public class OrderEventPublisher {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public OrderEventPublisher(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    // MANDATORY: refuses to run outside a transaction, which would silently defeat the pattern
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(String topic, String orderId, Object event) {
        jdbc.update("INSERT INTO outbox(topic, msg_key, payload) VALUES (?, ?, ?)",
                topic, orderId, mapper.writeValueAsString(event));
    }
}