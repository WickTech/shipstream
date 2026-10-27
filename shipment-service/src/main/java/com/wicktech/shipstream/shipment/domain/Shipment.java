package com.wicktech.shipstream.shipment.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "shipments")
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "shipment_id", updatable = false, nullable = false)
    private UUID shipmentId;

    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "carrier", nullable = false, length = 64)
    private String carrier;

    @Column(name = "tracking_number", nullable = false, length = 128)
    private String trackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ShipmentStatus status = ShipmentStatus.CREATED;

    @Column(name = "estimated_delivery")
    private LocalDate estimatedDelivery;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void onUpdate() { this.updatedAt = Instant.now(); }

    protected Shipment() {}

    public Shipment(UUID orderId, String carrier, String trackingNumber, LocalDate estimatedDelivery) {
        this.orderId = orderId;
        this.carrier = carrier;
        this.trackingNumber = trackingNumber;
        this.estimatedDelivery = estimatedDelivery;
    }

    public UUID getShipmentId()          { return shipmentId; }
    public UUID getOrderId()             { return orderId; }
    public String getCarrier()           { return carrier; }
    public String getTrackingNumber()    { return trackingNumber; }
    public ShipmentStatus getStatus()    { return status; }
    public LocalDate getEstimatedDelivery() { return estimatedDelivery; }
    public Instant getCreatedAt()        { return createdAt; }
    public Instant getUpdatedAt()        { return updatedAt; }

    public void updateStatus(ShipmentStatus newStatus) {
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }
}
