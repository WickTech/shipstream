package com.wicktech.shipstream.tracking.api;

import com.wicktech.shipstream.tracking.domain.TrackingEvent;

import java.time.Instant;
import java.util.UUID;

public record TrackingEventResponse(
    UUID    eventId,
    UUID    shipmentId,
    String  carrierCode,
    String  location,
    String  description,
    Instant occurredAt,
    Instant ingestedAt
) {
    public static TrackingEventResponse from(TrackingEvent e) {
        return new TrackingEventResponse(
            e.getEventId(), e.getShipmentId(), e.getCarrierCode(),
            e.getLocation(), e.getDescription(), e.getOccurredAt(), e.getIngestedAt()
        );
    }
}
