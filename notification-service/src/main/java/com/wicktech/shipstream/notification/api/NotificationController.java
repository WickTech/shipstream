package com.wicktech.shipstream.notification.api;

import com.wicktech.shipstream.notification.domain.Notification;
import com.wicktech.shipstream.notification.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** GET /notifications/{recipientId} */
    @GetMapping("/{recipientId}")
    public ResponseEntity<List<NotificationResponse>> list(@PathVariable UUID recipientId) {
        List<NotificationResponse> responses = notificationService.listForRecipient(recipientId)
            .stream().map(NotificationResponse::from).toList();
        return ResponseEntity.ok(responses);
    }

    public record NotificationResponse(UUID id, UUID recipientId, String eventType,
                                        String payload, Instant sentAt) {
        static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getRecipientId(),
                n.getEventType(), n.getPayload(), n.getSentAt());
        }
    }
}
