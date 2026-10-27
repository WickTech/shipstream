package com.wicktech.shipstream.tracking.service;

import com.wicktech.shipstream.tracking.domain.TrackingEvent;
import com.wicktech.shipstream.tracking.repository.TrackingEventRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class TrackingService {

    private final TrackingEventRepository trackingRepo;
    private final KafkaTemplate<String, byte[]> kafkaTemplate;

    public TrackingService(TrackingEventRepository trackingRepo,
                           KafkaTemplate<String, byte[]> kafkaTemplate) {
        this.trackingRepo  = trackingRepo;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Transactional
    public TrackingEvent ingest(UUID shipmentId, String carrierCode,
                                String location, String description, Instant occurredAt) {
        TrackingEvent event = new TrackingEvent(shipmentId, carrierCode, location, description, occurredAt);
        event = trackingRepo.save(event);

        // Publish inline — tracking ingest is fire-and-forget; no outbox needed here
        // (tracking-service is the producer, not a domain aggregate that needs ACID outbox)
        String payload = trackingPayload(event);
        kafkaTemplate.send("tracking.events", shipmentId.toString(), payload.getBytes());

        return event;
    }

    @Transactional(readOnly = true)
    public List<TrackingEvent> timeline(UUID shipmentId) {
        return trackingRepo.findByShipmentIdOrderByOccurredAtDesc(shipmentId);
    }

    private static String trackingPayload(TrackingEvent e) {
        String loc = e.getLocation() == null ? "null" : "\"" + e.getLocation() + "\"";
        return """
            {"eventId":"%s","shipmentId":"%s","carrierCode":"%s","location":%s,"description":"%s","occurredAt":"%s","ingestedAt":"%s"}
            """.formatted(e.getEventId(), e.getShipmentId(), e.getCarrierCode(),
                          loc, e.getDescription(), e.getOccurredAt(), e.getIngestedAt()).strip();
    }
}
