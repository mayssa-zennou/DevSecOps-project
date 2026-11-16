package com.mayssa.ordersaga.notification_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.mayssa.ordersaga.notification_service.events.OrderConfirmedEvent;
import com.mayssa.ordersaga.notification_service.repository.NotificationRepository;
import com.mayssa.ordersaga.notification_service.service.NotificationService;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@Testcontainers
class NotificationServiceIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgresql = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    NotificationService service;

    @Autowired
    NotificationRepository repository;

    @MockitoBean
    JavaMailSender mailSender;

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void mailFailure_rollsBackTheNotification_soRedeliveryCanRetry() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> service.handleOrderConfirmed(confirmed("evt-rollback")))
                .isInstanceOf(MailSendException.class);

        assertThat(repository.existsByEventId("evt-rollback")).isFalse();
    }

    @Test
    void duplicateDelivery_afterSuccess_isIgnored() {
        service.handleOrderConfirmed(confirmed("evt-dup"));
        service.handleOrderConfirmed(confirmed("evt-dup"));

        assertThat(repository.count()).isEqualTo(1);
        verify(mailSender, times(1)).send(any(SimpleMailMessage.class));
    }

    private static OrderConfirmedEvent confirmed(String eventId) {
        return new OrderConfirmedEvent(eventId, "order-42", "alice", "alice@test.com", Instant.now());
    }
}