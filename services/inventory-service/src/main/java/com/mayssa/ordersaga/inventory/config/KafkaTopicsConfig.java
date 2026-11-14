package com.mayssa.ordersaga.inventory.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {
    @Bean NewTopic stockReserved() { return TopicBuilder.name("stock.reserved").partitions(1).replicas(1).build(); }
    @Bean NewTopic stockRejected() { return TopicBuilder.name("stock.rejected").partitions(1).replicas(1).build(); }
}