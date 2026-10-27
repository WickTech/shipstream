package com.wicktech.shipstream.tracking.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wicktech.shipstream.tracking.service.HmacVerifier;
import com.wicktech.shipstream.tracking.service.TrackingService;
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
    private final ObjectMapper objectMapper;

    @Value("${tracking.webhook-secret:}")
    private String webhookSecret;

    public TrackingController(TrackingService trackingService,
                               HmacVerifier hmacVerifier,
                               ObjectMapper objectMapper) {
        this.trackingService = trackingService;
        this.hmacVerifier    = hmacVerifier;
        this.objectMapper    = objectMapper;
    }

    /** GET /tracking/{shipmentId} — timeline ordered newest-first */
    @GetMapping("/{shipmentId}")
    public ResponseEntity<List<TrackingEventResponse>> timeline(@PathVariable UUID shipmentId) {
        List<TrackingEventResponse> events = trackingService.timeline(shipmentId)
            .stream().map(TrackingEventResponse::from).toList();
        return ResponseEntity.ok(events);
    }

    /**
     * POST /tracking/ingest — carrier webhook.
     * Raw bytes received first so HMAC can be verified before deserialization.
     */
    @PostMapping(value = "/ingest", consumes = "application/json")
    public ResponseEntity<TrackingEventResponse> ingest(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String sig) throws Exception {

        if (!webhookSecret.isBlank()) {
            if (!hmacVerifier.verify(webhookSecret, sig, rawBody)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        }

        IngestRequest req = objectMapper.readValue(rawBody, IngestRequest.class);
        var event = trackingService.ingest(
            req.shipmentId(), req.carrierCode(), req.location(),
            req.description(), req.occurredAt()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(TrackingEventResponse.from(event));
    }
}
