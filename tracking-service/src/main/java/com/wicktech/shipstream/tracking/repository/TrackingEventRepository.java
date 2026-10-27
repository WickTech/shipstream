package com.wicktech.shipstream.tracking.repository;

import com.wicktech.shipstream.tracking.domain.TrackingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TrackingEventRepository extends JpaRepository<TrackingEvent, UUID> {
    List<TrackingEvent> findByShipmentIdOrderByOccurredAtDesc(UUID shipmentId);
}
