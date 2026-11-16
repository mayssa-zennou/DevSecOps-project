package com.mayssa.ordersaga.notification_service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import com.mayssa.ordersaga.notification_service.entities.Notification;
import com.mayssa.ordersaga.notification_service.events.OrderConfirmedEvent;
import com.mayssa.ordersaga.notification_service.events.OrderRejectedEvent;
import com.mayssa.ordersaga.notification_service.repository.NotificationRepository;
import com.mayssa.ordersaga.notification_service.service.NotificationService;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private NotificationService notificationService;

    // ---------- order.confirmed ----------

    @Test
    void orderConfirmed_duplicate_savesNothingAndSendsNothing() {
        when(notificationRepository.existsByEventId("evt-1")).thenReturn(true);

        notificationService.handleOrderConfirmed(confirmed("evt-1"));

        verify(notificationRepository, never()).save(any());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void orderConfirmed_newEvent_savesNotificationAndSendsMail() {
        when(notificationRepository.existsByEventId("evt-2")).thenReturn(false);

        notificationService.handleOrderConfirmed(confirmed("evt-2"));

        Notification notification = savedNotification();
        assertThat(notification.getEventId()).isEqualTo("evt-2");
        assertThat(notification.getUserId()).isEqualTo("alice");
        assertThat(notification.getType()).isEqualTo("ORDER_CONFIRMED");
        assertThat(notification.getMessage()).contains("order-42").contains("confirmed");

        SimpleMailMessage mail = sentMail();
        assertThat(mail.getTo()).containsExactly("alice@test.com");
        assertThat(mail.getSubject()).isEqualTo("Order Confirmed");
        assertThat(mail.getText()).contains("order-42");
    }

    @Test
    void orderConfirmed_persistsNotificationBeforeSendingMail() {
        when(notificationRepository.existsByEventId("evt-3")).thenReturn(false);

        notificationService.handleOrderConfirmed(confirmed("evt-3"));

        InOrder order = inOrder(notificationRepository, mailSender);
        order.verify(notificationRepository).save(any(Notification.class));
        order.verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void orderConfirmed_mailFailure_propagatesSoTheTransactionRollsBack() {
        when(notificationRepository.existsByEventId("evt-4")).thenReturn(false);
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> notificationService.handleOrderConfirmed(confirmed("evt-4")))
                .isInstanceOf(MailSendException.class);
    }

    // ---------- order.rejected ----------

    @Test
    void orderRejected_duplicate_savesNothingAndSendsNothing() {
        when(notificationRepository.existsByEventId("evt-5")).thenReturn(true);

        notificationService.handleOrderRejected(rejected("evt-5", "INSUFFICIENT_STOCK"));

        verify(notificationRepository, never()).save(any());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
            "INSUFFICIENT_STOCK, insufficient stock",
            "UNKNOWN_PRODUCT, unknown product",
            "SOMETHING_ELSE, unknown reason",
            "NULL, unknown reason"
    })
    void orderRejected_newEvent_explainsTheReason(String reason, String expectedText) {
        when(notificationRepository.existsByEventId("evt-6")).thenReturn(false);

        notificationService.handleOrderRejected(rejected("evt-6", reason));

        Notification notification = savedNotification();
        assertThat(notification.getEventId()).isEqualTo("evt-6");
        assertThat(notification.getUserId()).isEqualTo("alice");
        assertThat(notification.getType()).isEqualTo("ORDER_REJECTED");
        assertThat(notification.getMessage()).contains("order-42").contains(expectedText);

        SimpleMailMessage mail = sentMail();
        assertThat(mail.getTo()).containsExactly("alice@test.com");
        assertThat(mail.getSubject()).isEqualTo("Order rejected");
        assertThat(mail.getText()).contains(expectedText);
    }

    @Test
    void orderRejected_persistsNotificationBeforeSendingMail() {
        when(notificationRepository.existsByEventId("evt-7")).thenReturn(false);

        notificationService.handleOrderRejected(rejected("evt-7", "UNKNOWN_PRODUCT"));

        InOrder order = inOrder(notificationRepository, mailSender);
        order.verify(notificationRepository).save(any(Notification.class));
        order.verify(mailSender).send(any(SimpleMailMessage.class));
    }

    // ---------- helpers ----------

    private static OrderConfirmedEvent confirmed(String eventId) {
        return new OrderConfirmedEvent(eventId, "order-42", "alice", "alice@test.com", Instant.now());
    }

    private static OrderRejectedEvent rejected(String eventId, String reason) {
        return new OrderRejectedEvent(eventId, "order-42", "alice", "alice@test.com", reason, Instant.now());
    }

    private Notification savedNotification() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        return captor.getValue();
    }

    private SimpleMailMessage sentMail() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }
}