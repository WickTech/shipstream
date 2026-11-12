package com.wicktech.shipstream.tracking.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record IngestRequest(
    @NotNull                      UUID    shipmentId,
    @NotBlank @Size(max = 32)     String  carrierCode,
    @Size(max = 256)              String  location,      // nullable
    @NotBlank @Size(max = 4096)   String  description,
    @NotNull                      Instant occurredAt
) {}
