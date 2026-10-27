package com.wicktech.shipstream.notification.service;

import com.wicktech.shipstream.notification.domain.Notification;
import com.wicktech.shipstream.notification.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository repo;

    public NotificationService(NotificationRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public Notification send(UUID recipientId, String eventType, String payload) {
        return repo.save(new Notification(recipientId, eventType, payload));
    }

    public List<Notification> listForRecipient(UUID recipientId) {
        return repo.findByRecipientIdOrderBySentAtDesc(recipientId);
    }
}
