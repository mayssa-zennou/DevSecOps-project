package com.mayssa.ordersaga.notification_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.mayssa.ordersaga.notification_service.events.OrderConfirmedEvent;
import com.mayssa.ordersaga.notification_service.entities.Notification;
import com.mayssa.ordersaga.notification_service.repository.NotificationRepository;
import com.mayssa.ordersaga.notification_service.service.NotificationService;

@SpringBootTest(properties = "spring.kafka.listener.auto-startup=false")
@AutoConfigureMockMvc
@Testcontainers
class NotificationServiceIntegrationTest {

    private static final String ALICE = "11111111-1111-1111-1111-111111111111";
    private static final String BOB = "22222222-2222-2222-2222-222222222222";

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgresql = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    NotificationService service;

    @Autowired
    NotificationRepository repository;

    @Autowired
    MockMvc mvc;

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

    @Test
    void listNotifications_customerSeesOnlyOwnNotifications() throws Exception {
        repository.save(notification("evt-alice", ALICE));
        repository.save(notification("evt-bob", BOB));

        mvc.perform(get("/notifications").with(customerToken(ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].userId").value(ALICE));
    }

    @Test
    void getNotification_ownedByAnotherCustomer_returns404() throws Exception {
        Notification saved = repository.save(notification("evt-private", ALICE));

        mvc.perform(get("/notifications/" + saved.getId()).with(customerToken(BOB)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getNotificationByUser_customerIsForbidden_butAdminIsAllowed() throws Exception {
        repository.save(notification("evt-user", ALICE));

        mvc.perform(get("/notifications/user/" + ALICE).with(customerToken(ALICE)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/notifications/user/" + ALICE).with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    private static Notification notification(String eventId, String userId) {
        return new Notification(0, eventId, userId, "ORDER_CONFIRMED", "Order confirmed", Instant.now());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor customerToken(String subject) {
        return jwt().jwt(token -> token.subject(subject))
                .authorities(new SimpleGrantedAuthority("SCOPE_notification:read"));
    }

    private static OrderConfirmedEvent confirmed(String eventId) {
        return new OrderConfirmedEvent(eventId, "order-42", "alice", "alice@test.com", Instant.now());
    }
}
