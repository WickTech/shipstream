package com.wicktech.shipstream.tracking.api;

import java.time.Instant;
import java.util.UUID;

public record IngestRequest(
    UUID    shipmentId,
    String  carrierCode,
    String  location,      // nullable
    String  description,
    Instant occurredAt
) {}
