package com.wicktech.shipstream.tracking.service;

import com.wicktech.shipstream.tracking.config.TrackingProperties;
import com.wicktech.shipstream.tracking.service.HmacVerifier.Result;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HmacVerifierTest {

    private static final String SECRET = "unit-test-secret-0123456789";
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final byte[] BODY = "{\"carrierCode\":\"UPS\"}".getBytes(StandardCharsets.UTF_8);

    private final HmacVerifier verifier = new HmacVerifier(
        new TrackingProperties(SECRET, Duration.ofMinutes(5)),
        Clock.fixed(NOW, ZoneOffset.UTC));

    private static String ts(Instant at) { return Long.toString(at.getEpochSecond()); }

    @Test
    void validSignatureAccepted() {
        String t = ts(NOW);
        assertThat(verifier.verify(t, verifier.sign(t, BODY), BODY)).isEqualTo(Result.VALID);
    }

    @Test
    void knownVectorMatchesTimestampDotBodyFormat() {
        // HMAC-SHA256("unit-test-secret-0123456789", "1767268800.{\"carrierCode\":\"UPS\"}"),
        // computed independently with: printf '%s' '1767268800.{"carrierCode":"UPS"}' |
        //   openssl dgst -sha256 -hmac 'unit-test-secret-0123456789'
        assertThat(verifier.sign("1767268800", BODY))
            .isEqualTo("sha256=" + KNOWN_VECTOR_HEX);
    }

    private static final String KNOWN_VECTOR_HEX =
        "9a7fa6db60c6a4a9f90d0efa160216a0976083c670346d221f5cb9a12e22cd30";

    @Test
    void missingTimestampRejected() {
        assertThat(verifier.verify(null, verifier.sign(ts(NOW), BODY), BODY)).isEqualTo(Result.MISSING_TIMESTAMP);
        assertThat(verifier.verify(" ", verifier.sign(ts(NOW), BODY), BODY)).isEqualTo(Result.MISSING_TIMESTAMP);
    }

    @Test
    void malformedTimestampRejected() {
        assertThat(verifier.verify("yesterday", "sha256=00", BODY)).isEqualTo(Result.MALFORMED_TIMESTAMP);
        assertThat(verifier.verify(Long.toString(Long.MAX_VALUE), "sha256=00", BODY))
            .isEqualTo(Result.MALFORMED_TIMESTAMP);
    }

    @Test
    void missingSignatureRejected() {
        assertThat(verifier.verify(ts(NOW), null, BODY)).isEqualTo(Result.MISSING_SIGNATURE);
    }

    @Test
    void wrongSignatureRejected() {
        String t = ts(NOW);
        String otherKey = new HmacVerifier(new TrackingProperties("another-secret-0123456789", Duration.ofMinutes(5)),
                                           Clock.fixed(NOW, ZoneOffset.UTC)).sign(t, BODY);
        assertThat(verifier.verify(t, otherKey, BODY)).isEqualTo(Result.BAD_SIGNATURE);
        assertThat(verifier.verify(t, "sha256=badbadbadbad", BODY)).isEqualTo(Result.BAD_SIGNATURE);
        assertThat(verifier.verify(t, "sha256=not-hex", BODY)).isEqualTo(Result.BAD_SIGNATURE);
        assertThat(verifier.verify(t, verifier.sign(t, BODY).substring("sha256=".length()), BODY))
            .isEqualTo(Result.BAD_SIGNATURE);
    }

    @Test
    void tamperedBodyRejected() {
        String t = ts(NOW);
        byte[] tampered = "{\"carrierCode\":\"DHL\"}".getBytes(StandardCharsets.UTF_8);
        assertThat(verifier.verify(t, verifier.sign(t, BODY), tampered)).isEqualTo(Result.BAD_SIGNATURE);
    }

    @Test
    void signatureBoundToTimestamp() {
        // Re-using a valid signature with a different (fresh) timestamp must fail.
        String signedTs = ts(NOW.minusSeconds(10));
        String sig = verifier.sign(signedTs, BODY);
        assertThat(verifier.verify(ts(NOW), sig, BODY)).isEqualTo(Result.BAD_SIGNATURE);
    }

    @Test
    void staleTimestampRejected() {
        String t = ts(NOW.minus(Duration.ofMinutes(5)).minusSeconds(1));
        assertThat(verifier.verify(t, verifier.sign(t, BODY), BODY)).isEqualTo(Result.TIMESTAMP_OUT_OF_WINDOW);
    }

    @Test
    void futureSkewedTimestampRejected() {
        String t = ts(NOW.plus(Duration.ofMinutes(5)).plusSeconds(1));
        assertThat(verifier.verify(t, verifier.sign(t, BODY), BODY)).isEqualTo(Result.TIMESTAMP_OUT_OF_WINDOW);
    }

    @Test
    void timestampAtWindowEdgeAccepted() {
        String past = ts(NOW.minus(Duration.ofMinutes(5)));
        String future = ts(NOW.plus(Duration.ofMinutes(5)));
        assertThat(verifier.verify(past, verifier.sign(past, BODY), BODY)).isEqualTo(Result.VALID);
        assertThat(verifier.verify(future, verifier.sign(future, BODY), BODY)).isEqualTo(Result.VALID);
    }

    @Test
    void blankSecretRefusedAtConstruction() {
        assertThatThrownBy(() -> new HmacVerifier(new TrackingProperties(" ", Duration.ofMinutes(5))))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("tracking.webhook-secret");
    }
}
