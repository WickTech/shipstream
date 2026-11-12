package com.wicktech.shipstream.tracking.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Carrier webhook settings. Validated at startup: the service refuses to start
 * without a webhook secret, so the ingest endpoint can never run unauthenticated.
 *
 * @param webhookSecret    shared HMAC-SHA256 key ({@code TRACKING_WEBHOOK_SECRET})
 * @param webhookTolerance max allowed difference between the signed timestamp and
 *                         the server clock, in either direction ({@code TRACKING_WEBHOOK_TOLERANCE})
 */
@Validated
@ConfigurationProperties(prefix = "tracking")
public record TrackingProperties(
    @NotBlank(message = "must be set (env TRACKING_WEBHOOK_SECRET); the tracking webhook rejects unsigned requests")
    @Size(min = 16, message = "must be at least 16 characters (env TRACKING_WEBHOOK_SECRET)")
    String webhookSecret,

    @NotNull
    @DurationMin(seconds = 1)
    @DefaultValue("5m")
    Duration webhookTolerance
) {
    @Override
    public String toString() {
        return "TrackingProperties[webhookSecret=***, webhookTolerance=" + webhookTolerance + "]";
    }
}
