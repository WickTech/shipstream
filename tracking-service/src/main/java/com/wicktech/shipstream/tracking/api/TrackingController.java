package com.wicktech.shipstream.tracking.api;

import com.wicktech.shipstream.tracking.service.HmacVerifier;
import com.wicktech.shipstream.tracking.service.TrackingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tracking")
public class TrackingController {

    private final TrackingService trackingService;
    private final HmacVerifier hmacVerifier;

    @Value("${tracking.webhook-secret:}")
    private String webhookSecret;

    public TrackingController(TrackingService trackingService, HmacVerifier hmacVerifier) {
        this.trackingService = trackingService;
        this.hmacVerifier    = hmacVerifier;
    }

    /** GET /tracking/{shipmentId} — timeline ordered newest-first */
    @GetMapping("/{shipmentId}")
    public ResponseEntity<List<TrackingEventResponse>> timeline(@PathVariable UUID shipmentId) {
        List<TrackingEventResponse> events = trackingService.timeline(shipmentId)
            .stream().map(TrackingEventResponse::from).toList();
        return ResponseEntity.ok(events);
    }

    /** POST /tracking/ingest — carrier webhook, HMAC-SHA256 verified when secret is set */
    @PostMapping("/ingest")
    public ResponseEntity<TrackingEventResponse> ingest(
            @RequestBody IngestRequest req,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String sig,
            HttpServletRequest httpReq) {

        if (!webhookSecret.isBlank()) {
            byte[] body = getBodyBytes(httpReq);
            if (!hmacVerifier.verify(webhookSecret, sig, body)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        }

        var event = trackingService.ingest(
            req.shipmentId(), req.carrierCode(), req.location(),
            req.description(), req.occurredAt()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(TrackingEventResponse.from(event));
    }

    private byte[] getBodyBytes(HttpServletRequest req) {
        try {
            return req.getInputStream().readAllBytes();
        } catch (Exception e) {
            return new byte[0];
        }
    }
}
