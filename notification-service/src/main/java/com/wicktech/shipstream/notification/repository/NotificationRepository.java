package com.wicktech.shipstream.notification.repository;

import com.wicktech.shipstream.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByRecipientIdOrderBySentAtDesc(UUID recipientId);
}
