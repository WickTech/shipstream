package com.wicktech.shipstream.shipment.api;

import com.wicktech.shipstream.shipment.domain.Shipment;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ShipmentResponse(
    UUID       shipmentId,
    UUID       orderId,
    String     carrier,
    String     trackingNumber,
    String     status,
    LocalDate  estimatedDelivery,
    Instant    createdAt,
    Instant    updatedAt
) {
    public static ShipmentResponse from(Shipment s) {
        return new ShipmentResponse(
            s.getShipmentId(),
            s.getOrderId(),
            s.getCarrier(),
            s.getTrackingNumber(),
            s.getStatus().name(),
            s.getEstimatedDelivery(),
            s.getCreatedAt(),
            s.getUpdatedAt()
        );
    }
}
