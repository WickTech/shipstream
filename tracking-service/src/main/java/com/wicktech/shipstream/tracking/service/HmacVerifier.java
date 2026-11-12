package com.wicktech.shipstream.tracking.service;

import com.wicktech.shipstream.tracking.config.TrackingProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Verifies carrier webhook signatures.
 *
 * <p>Wire format:
 * <pre>
 *   X-Shipstream-Timestamp: &lt;unix epoch seconds&gt;
 *   X-Shipstream-Signature: sha256=&lt;hex(HMAC-SHA256(secret, timestamp + "." + rawBody))&gt;
 * </pre>
 * The timestamp must lie within {@code tracking.webhook-tolerance} of the server
 * clock (past or future). The comparison is constant-time.
 */
@Component
public class HmacVerifier {

    public static final String TIMESTAMP_HEADER = "X-Shipstream-Timestamp";
    public static final String SIGNATURE_HEADER = "X-Shipstream-Signature";

    private static final String PREFIX = "sha256=";
    private static final String ALGORITHM = "HmacSHA256";

    public enum Result {
        VALID,
        MISSING_TIMESTAMP,
        MALFORMED_TIMESTAMP,
        TIMESTAMP_OUT_OF_WINDOW,
        MISSING_SIGNATURE,
        BAD_SIGNATURE;

        public boolean isValid() { return this == VALID; }
    }

    private final byte[] key;
    private final Duration tolerance;
    private final Clock clock;

    @Autowired
    public HmacVerifier(TrackingProperties props) {
        this(props, Clock.systemUTC());
    }

    public HmacVerifier(TrackingProperties props, Clock clock) {
        if (props.webhookSecret() == null || props.webhookSecret().isBlank()) {
            throw new IllegalStateException("tracking.webhook-secret must be set");
        }
        this.key       = props.webhookSecret().getBytes(StandardCharsets.UTF_8);
        this.tolerance = props.webhookTolerance();
        this.clock     = clock;
    }

    public Result verify(String timestampHeader, String signatureHeader, byte[] body) {
        if (timestampHeader == null || timestampHeader.isBlank()) return Result.MISSING_TIMESTAMP;

        Instant signedAt;
        try {
            signedAt = Instant.ofEpochSecond(Long.parseLong(timestampHeader.strip()));
        } catch (NumberFormatException | DateTimeException e) {
            return Result.MALFORMED_TIMESTAMP;
        }
        if (Duration.between(signedAt, clock.instant()).abs().compareTo(tolerance) > 0) {
            return Result.TIMESTAMP_OUT_OF_WINDOW;
        }

        if (signatureHeader == null || signatureHeader.isBlank()) return Result.MISSING_SIGNATURE;
        if (!signatureHeader.startsWith(PREFIX)) return Result.BAD_SIGNATURE;

        byte[] provided;
        try {
            provided = HexFormat.of().parseHex(signatureHeader.substring(PREFIX.length()));
        } catch (IllegalArgumentException e) {
            return Result.BAD_SIGNATURE;
        }
        byte[] expected = mac(timestampHeader, body);
        return MessageDigest.isEqual(expected, provided) ? Result.VALID : Result.BAD_SIGNATURE;
    }

    /** Produces the {@value #SIGNATURE_HEADER} value for a request (used by senders and tests). */
    public String sign(String timestamp, byte[] body) {
        return PREFIX + HexFormat.of().formatHex(mac(timestamp, body));
    }

    private byte[] mac(String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(new SecretKeySpec(key, ALGORITHM));
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            return mac.doFinal(body);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
