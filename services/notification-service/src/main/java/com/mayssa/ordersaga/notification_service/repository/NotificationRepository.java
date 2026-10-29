package com.mayssa.ordersaga.notification_service.repository;

import com.mayssa.ordersaga.notification_service.entities.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification,Long> {
    boolean existByEventId(String eventId);
    List<Notification> findById(String userId);
}
