package com.mayssa.ordersaga.notification_service.controller;

import com.mayssa.ordersaga.notification_service.entities.Notification;
import com.mayssa.ordersaga.notification_service.repository.NotificationRepository;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@AllArgsConstructor
@RestController
@RequestMapping("/notifications")
public class NotificationController {
    private final NotificationRepository notificationRepo;
    @GetMapping
    public List<Notification> all(@AuthenticationPrincipal Jwt jwt, Authentication auth){
        return (isAdmin(auth) ? notificationRepo.findAll() : notificationRepo.findByUserId(jwt.getSubject()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Notification> byId(@PathVariable long id, @AuthenticationPrincipal Jwt jwt, Authentication auth){
        Optional<Notification> notification = isAdmin(auth)
                ? notificationRepo.findById(id)
                : notificationRepo.findByIdAndUserId(id, jwt.getSubject());
        return notification.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/user/{userId}")
    public List<Notification> byUser(@PathVariable String userId){
        return notificationRepo.findByUserId(userId);
    }

    private boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_admin"));
    }
}
