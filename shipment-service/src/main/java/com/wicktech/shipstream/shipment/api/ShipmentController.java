package com.wicktech.shipstream.shipment.api;

import com.wicktech.shipstream.shipment.domain.Shipment;
import com.wicktech.shipstream.shipment.service.ShipmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/shipments")
public class ShipmentController {

    private final ShipmentService shipmentService;

    public ShipmentController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ShipmentResponse> getByOrderId(@PathVariable UUID orderId) {
        try {
            return ResponseEntity.ok(ShipmentResponse.from(shipmentService.getByOrderId(orderId)));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
