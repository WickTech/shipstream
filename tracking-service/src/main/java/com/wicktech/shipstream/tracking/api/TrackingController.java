package com.wicktech.shipstream.tracking.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wicktech.shipstream.tracking.service.HmacVerifier;
import com.wicktech.shipstream.tracking.service.TrackingService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/tracking")
public class TrackingController {

    private static final Logger log = LoggerFactory.getLogger(TrackingController.class);

    private final TrackingService trackingService;
    private final HmacVerifier hmacVerifier;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public TrackingController(TrackingService trackingService,
                               HmacVerifier hmacVerifier,
                               ObjectMapper objectMapper,
                               Validator validator) {
        this.trackingService = trackingService;
        this.hmacVerifier    = hmacVerifier;
        this.objectMapper    = objectMapper;
        this.validator       = validator;
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
     * Raw bytes are received first so the HMAC can be verified before deserialization;
     * bean validation then runs on the parsed body (it cannot use {@code @Valid} on byte[]).
     */
    @PostMapping(value = "/ingest", consumes = "application/json")
    public ResponseEntity<TrackingEventResponse> ingest(
            @RequestBody byte[] rawBody,
            @RequestHeader(value = HmacVerifier.TIMESTAMP_HEADER, required = false) String timestamp,
            @RequestHeader(value = HmacVerifier.SIGNATURE_HEADER, required = false) String signature) {

        HmacVerifier.Result result = hmacVerifier.verify(timestamp, signature, rawBody);
        if (!result.isValid()) {
            log.warn("Rejected tracking webhook: {}", result);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        IngestRequest req;
        try {
            req = objectMapper.readValue(rawBody, IngestRequest.class);
        } catch (IOException e) {   // includes Jackson parse/mapping errors
            return ResponseEntity.badRequest().build();
        }
        if (req == null) {          // literal JSON "null"
            return ResponseEntity.badRequest().build();
        }
        Set<ConstraintViolation<IngestRequest>> violations = validator.validate(req);
        if (!violations.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        var event = trackingService.ingest(
            req.shipmentId(), req.carrierCode(), req.location(),
            req.description(), req.occurredAt()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(TrackingEventResponse.from(event));
    }
}
