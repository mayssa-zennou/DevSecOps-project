package com.mayssa.ordersaga.order.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicsConfig {
    @Bean NewTopic orderCreated()   { return TopicBuilder.name("order.created").partitions(1).replicas(1).build(); }
    @Bean NewTopic orderConfirmed() { return TopicBuilder.name("order.confirmed").partitions(1).replicas(1).build(); }
    @Bean NewTopic orderRejected()  { return TopicBuilder.name("order.rejected").partitions(1).replicas(1).build(); }
}