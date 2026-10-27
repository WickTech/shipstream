package com.wicktech.shipstream.tracking.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "tracking_events")
public class TrackingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "event_id", updatable = false, nullable = false)
    private UUID eventId;

    @Column(name = "shipment_id", nullable = false)
    private UUID shipmentId;

    @Column(name = "carrier_code", nullable = false, length = 32)
    private String carrierCode;

    @Column(name = "location", length = 256)
    private String location;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "ingested_at", nullable = false, updatable = false)
    private Instant ingestedAt = Instant.now();

    protected TrackingEvent() {}

    public TrackingEvent(UUID shipmentId, String carrierCode, String location,
                         String description, Instant occurredAt) {
        this.shipmentId  = shipmentId;
        this.carrierCode = carrierCode;
        this.location    = location;
        this.description = description;
        this.occurredAt  = occurredAt;
    }

    public UUID    getEventId()    { return eventId; }
    public UUID    getShipmentId() { return shipmentId; }
    public String  getCarrierCode(){ return carrierCode; }
    public String  getLocation()   { return location; }
    public String  getDescription(){ return description; }
    public Instant getOccurredAt() { return occurredAt; }
    public Instant getIngestedAt() { return ingestedAt; }
}
