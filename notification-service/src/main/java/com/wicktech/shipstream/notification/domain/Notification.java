package com.wicktech.shipstream.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(columnDefinition = "jsonb", nullable = false)
    private String payload;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt = Instant.now();

    protected Notification() {}

    public Notification(UUID recipientId, String eventType, String payload) {
        this.recipientId = recipientId;
        this.eventType   = eventType;
        this.payload     = payload;
    }

    public UUID getId()          { return id; }
    public UUID getRecipientId() { return recipientId; }
    public String getEventType() { return eventType; }
    public String getPayload()   { return payload; }
    public Instant getSentAt()   { return sentAt; }
}
