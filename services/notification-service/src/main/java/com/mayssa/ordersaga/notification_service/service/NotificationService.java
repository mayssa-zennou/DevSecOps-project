package com.mayssa.ordersaga.notification_service.service;

import com.mayssa.ordersaga.notification_service.entities.Notification;
import com.mayssa.ordersaga.notification_service.events.OrderConfirmedEvent;
import com.mayssa.ordersaga.notification_service.events.OrderRejectedEvent;
import com.mayssa.ordersaga.notification_service.repository.NotificationRepository;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@AllArgsConstructor
public class NotificationService {
    private static final Logger log= LoggerFactory.getLogger(NotificationService.class);
    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;

    @Transactional
    public void handleOrderConfirmed(OrderConfirmedEvent event){
        if(notificationRepository.existsByEventId(event.eventId())){
            log.info("Event {} is already treated and will be ignored", event.eventId());
            return;
        }
        String message="Your order #" + event.orderId() + " is confirmed";
        save(event.eventId(),event.userId(),"ORDER_CONFIRMED",message);
        sendEmail(event.userEmail(),"Order Confirmed",message);
        log.info("Notification ORDER_CONFIRMED sent to {}", event.userEmail());
    }
    
    @Transactional
    public void handleOrderRejected(OrderRejectedEvent event){
        if (notificationRepository.existsByEventId(event.eventId())){
            log.info("Event {} is already treated and will be ignored", event.eventId());
            return;
        }
        String message = "Your order #" + event.orderId() + " is rejected: " + describe(event.reason());
        save(event.eventId(), event.userId(), "ORDER_REJECTED", message);
        sendEmail(event.userEmail(), "Order rejected", message);
        log.info("Notification ORDER_REJECTED sent to {}", event.userEmail());
    }

    private static String describe(String reason) {
        if (reason == null) {
            return "unknown reason";
        }
        return switch (reason) {
            case "INSUFFICIENT_STOCK" -> "insufficient stock";
            case "UNKNOWN_PRODUCT" -> "unknown product";
            default -> "unknown reason";
        };
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
