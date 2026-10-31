package com.mayssa.ordersaga.notification_service.controller;

import com.mayssa.ordersaga.notification_service.entities.Notification;
import com.mayssa.ordersaga.notification_service.repository.NotificationRepository;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@AllArgsConstructor
@RestController
@RequestMapping("/notifications") // c'est l'@ du controller tous les url de ce controller commence par /noti..
public class NotificationController {
    private final NotificationRepository notificationRepo;
    @GetMapping
    public List<Notification> all(){
        return notificationRepo.findAll();
    }

    @GetMapping("/user/{userId}")
    public List<Notification> byUser(@PathVariable String userId){
        return notificationRepo.findById(userId);
    }
}
