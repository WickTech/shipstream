package com.wicktech.shipstream.shipment.service;

import com.wicktech.shipstream.shipment.domain.Shipment;
import com.wicktech.shipstream.shipment.domain.ShipmentStatus;
import com.wicktech.shipstream.shipment.outbox.OutboxEvent;
import com.wicktech.shipstream.shipment.outbox.OutboxRepository;
import com.wicktech.shipstream.shipment.repository.ShipmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final OutboxRepository outboxRepository;

    public ShipmentService(ShipmentRepository shipmentRepository, OutboxRepository outboxRepository) {
        this.shipmentRepository = shipmentRepository;
        this.outboxRepository   = outboxRepository;
    }

    @Transactional
    public Shipment createForOrder(UUID orderId) {
        // Idempotent — skip if already created for this order
        return shipmentRepository.findByOrderId(orderId).orElseGet(() -> {
            String carrier        = "FEDEX";
            String trackingNumber = "FX-" + orderId.toString().substring(0, 8).toUpperCase();
            LocalDate estimated   = LocalDate.now().plusDays(5);

            Shipment shipment = new Shipment(orderId, carrier, trackingNumber, estimated);
            shipment = shipmentRepository.save(shipment);

            outboxRepository.save(new OutboxEvent(
                "Shipment",
                shipment.getShipmentId().toString(),
                "ShipmentCreated",
                shipmentCreatedPayload(shipment)
            ));

            return shipment;
        });
    }

    @Transactional(readOnly = true)
    public Shipment getByOrderId(UUID orderId) {
        return shipmentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new NoSuchElementException("No shipment for order: " + orderId));
    }

    @Transactional
    public Shipment updateStatus(UUID shipmentId, ShipmentStatus newStatus) {
        Shipment shipment = shipmentRepository.findById(shipmentId)
            .orElseThrow(() -> new NoSuchElementException("Shipment not found: " + shipmentId));

        ShipmentStatus old = shipment.getStatus();
        shipment.updateStatus(newStatus);
        shipment = shipmentRepository.save(shipment);

        outboxRepository.save(new OutboxEvent(
            "Shipment",
            shipment.getShipmentId().toString(),
            "ShipmentStatusChanged",
            statusChangedPayload(shipment, old, newStatus)
        ));

        return shipment;
    }

    // ── payload builders ─────────────────────────────────────────────────────

    private static String shipmentCreatedPayload(Shipment s) {
        return """
            {"shipmentId":"%s","orderId":"%s","carrier":"%s","trackingNumber":"%s","estimatedDelivery":"%s","createdAt":"%s"}
            """.formatted(s.getShipmentId(), s.getOrderId(), s.getCarrier(),
                          s.getTrackingNumber(), s.getEstimatedDelivery(), s.getCreatedAt()).strip();
    }

    private static String statusChangedPayload(Shipment s, ShipmentStatus old, ShipmentStatus newS) {
        return """
            {"shipmentId":"%s","orderId":"%s","oldStatus":"%s","newStatus":"%s","changedAt":"%s"}
            """.formatted(s.getShipmentId(), s.getOrderId(), old, newS, s.getUpdatedAt()).strip();
    }
}
