package com.mayssa.ordersaga.notification_service;

import com.mayssa.ordersaga.notification_service.entities.Notification;
import com.mayssa.ordersaga.notification_service.event.OrderCreatedEvent;
import com.mayssa.ordersaga.notification_service.event.StockLowEvent;
import com.mayssa.ordersaga.notification_service.repository.NotificationRepository;
import com.mayssa.ordersaga.notification_service.service.NotificationService;
import lombok.AllArgsConstructor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Instant;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private NotificationService notificationService;

    @Test
    void orderCreated_doublon_nEnvoieRienEtNeSauvegardePas(){
        //etape 1 :preparer le mock : quand te demande est ce que event1 exist repond par oui
        when(notificationRepository.existsByEventId("event-1")).thenReturn(true);

        OrderCreatedEvent event=new OrderCreatedEvent("event-1", "5L", "alice", "alice@test.com", Instant.now());
        //etape 2 agir : lancer la vrai service
        notificationService.handleOrderCreated(event);

        //etape 3 :verifier : en lit les deux cachier des faux puisqu'ils conservent le resultat
        verify(notificationRepository,never()).save(any());
        verify(mailSender,never()).send(any(SimpleMailMessage.class));
    }


    @Test
    void orderCreated_nouveau_envoieEmailEtSauvefarde(){
        when(notificationRepository.existsByEventId("event-2")).thenReturn(false);
        OrderCreatedEvent event=new OrderCreatedEvent("event-2", "5L", "alice", "alice@test.com", Instant.now());
        notificationService.handleOrderCreated(event);

        ArgumentCaptor<SimpleMailMessage> capture=ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(capture.capture());
        SimpleMailMessage mail=capture.getValue();
        assertThat(mail.getTo()).containsExactly("alice@test.com");

        ArgumentCaptor<Notification> notifCapture=ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notifCapture.capture());
        Notification notification=notifCapture.getValue();
        assertThat(notification.getEventId()).isEqualTo("event-2");
        assertThat(notification.getUserId()).isEqualTo("alice");
        assertThat(notification.getType()).isEqualTo("ORDER_CREATED");

    }

    @Test
    void stockLow_doublon_neSauvegardepas(){
        when(notificationRepository.existsByEventId("evt-3")).thenReturn(true);
        StockLowEvent stockEvent=new StockLowEvent("evt-3", "12L", "Clavier", "3", Instant.now());
        notificationService.handleStockLow(stockEvent);
        verify(notificationRepository,never()).save(any());
    }

    @Test
    void stockLow_nouveau_sauvegarde(){
        when(notificationRepository.existsByEventId("evt-3")).thenReturn(false);
        StockLowEvent stockEvent=new StockLowEvent("evt-3", "12L", "Clavier", "3", Instant.now());
        notificationService.handleStockLow(stockEvent);

        ArgumentCaptor<Notification> capture=ArgumentCaptor.forClass(Notification.class);

        verify(notificationRepository).save(capture.capture());
        Notification notif=capture.getValue();
        assertThat(notif.getEventId()).isEqualTo("evt-3");
        assertThat(notif.getType()).isEqualTo("STOCK_LOW");

    }



}
