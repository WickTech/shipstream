package com.wicktech.shipstream.shipment.repository;

import com.wicktech.shipstream.shipment.domain.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {
    Optional<Shipment> findByOrderId(UUID orderId);
}
