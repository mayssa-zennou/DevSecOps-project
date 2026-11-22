package com.mayssa.ordersaga.notification_service.repository;

import com.mayssa.ordersaga.notification_service.entities.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification,Long> {
    boolean existsByEventId(String eventId);
    List<Notification> findByUserId(String userId);
    Optional<Notification> findByIdAndUserId(long id, String userId);
}
