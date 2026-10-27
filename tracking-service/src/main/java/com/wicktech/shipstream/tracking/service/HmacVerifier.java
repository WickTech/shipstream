package com.wicktech.shipstream.tracking.service;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class HmacVerifier {

    /**
     * Returns true when sig matches "sha256=<hex(HMAC-SHA256(secret, body))>".
     * Also returns true when secret is blank (HMAC disabled).
     */
    public boolean verify(String secret, String sig, byte[] body) {
        if (secret == null || secret.isBlank()) return true;
        if (sig == null || !sig.startsWith("sha256=")) return false;
        String expected = "sha256=" + hmac(secret, body);
        return constantTimeEquals(expected, sig);
    }

    public String sign(String secret, byte[] body) {
        return "sha256=" + hmac(secret, body);
    }

    private static String hmac(String secret, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(body));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }
}
