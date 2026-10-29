package com.mayssa.ordersaga.notification_service.service;

import com.mayssa.ordersaga.notification_service.entities.Notification;
import com.mayssa.ordersaga.notification_service.event.OrderCreatedEvent;
import com.mayssa.ordersaga.notification_service.event.StockLowEvent;
import com.mayssa.ordersaga.notification_service.repository.NotificationRepository;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class NotificationService {
    private static final Logger log= LoggerFactory.getLogger(NotificationService.class);
    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;

    public void handleOrderCreated(OrderCreatedEvent event){
        if(notificationRepository.existByEventId(event.eventId())){
            log.info("Événement {} déjà traité, ignoré", event.eventId());
            return;
        }
        String message="Votre commande #" + event.orderId() + " est confirmée";
        sendEmail(event.userEmail(),"Commande confirmé",message);
        save(event.eventId(),event.userId(),"ORDER_CREATED",message);
        log.info("Notification ORDER_CREATED envoyée à {}", event.userEmail());
    }

    public void handleStockLow(StockLowEvent event){
        if (notificationRepository.existByEventId(event.eventId())){
            log.info("Événement {} déjà traité, ignoré", event.eventId());
            return;
        }
        String message="Stock bas : " + e.productName() + " (reste " + e.remaining() + ")";
        save(event.eventId(),"admin","STOCK_LOW",message);
        log.info("Notification STOCK_LOW enregistrée");
    }



    public void save(String eventId,String userId,String type,String message){
        Notification notification=new Notification();
        notification.setEventId(eventId);
        notification.setUserId(userId);
        notification.setType(type);
        notification.setMessage(message);
        notificationRepository.save(notification);
    }

    public void sendEmail(String to,String subject,String body){
        SimpleMailMessage m=new SimpleMailMessage();
        m.setFrom("no-reply@project.local");
        m.setTo(to);
        m.setSubject(subject);
        m.setText(body);
        mailSender.send(m);
    }

}
