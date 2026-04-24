package com.codereviewbot.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Verifies that incoming webhook requests actually came from GitHub.
 *
 * HOW IT WORKS:
 *  1. GitHub computes HMAC-SHA256 of the raw request body using your webhook secret.
 *  2. GitHub sends the result in the "X-Hub-Signature-256" header as "sha256=<hex>".
 *  3. We compute the same HMAC on our side and compare.
 *  4. If they match → request is genuine. If not → reject with 403.
 *
 * SECURITY NOTE: We use MessageDigest.isEqual() for comparison (constant-time),
 * NOT String.equals(). This prevents timing-attack exploits.
 */
@Slf4j
@Component
public class WebhookSignatureVerifier {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_PREFIX = "sha256=";

    @Value("${github.webhook.secret}")
    private String webhookSecret;

    /**
     * Returns true if the signature header matches the computed HMAC of the body.
     *
     * @param rawBody       the raw bytes of the request body (must be read before Jackson parses it)
     * @param signatureHeader the value of the X-Hub-Signature-256 header
     */
    public boolean isValid(byte[] rawBody, String signatureHeader) {
        // If GitHub didn't send a signature at all — reject
        if (signatureHeader == null || !signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            log.warn("Webhook rejected: missing or malformed X-Hub-Signature-256 header");
            return false;
        }

        try {
            // Compute HMAC-SHA256 of the raw body using our secret
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM
            );
            mac.init(keySpec);
            byte[] computedHash = mac.doFinal(rawBody);

            // Convert to hex string
            String computedSignature = SIGNATURE_PREFIX + bytesToHex(computedHash);

            // Constant-time comparison — prevents timing attacks
            boolean valid = MessageDigest.isEqual(
                computedSignature.getBytes(StandardCharsets.UTF_8),
                signatureHeader.getBytes(StandardCharsets.UTF_8)
            );

            if (!valid) {
                log.warn("Webhook rejected: signature mismatch. Possible replay or forgery attempt.");
            }

            return valid;

        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("HMAC computation failed: {}", e.getMessage());
            return false;
        }
    }

    /** Converts a byte array to lowercase hex string (e.g. [0xAB, 0xCD] → "abcd") */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
