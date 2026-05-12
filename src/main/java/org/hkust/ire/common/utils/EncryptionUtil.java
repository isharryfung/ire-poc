package org.hkust.ire.common.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.util.Base64;

/**
 * Encryption and hashing utilities for the IRE system.
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
public final class EncryptionUtil {

    private static final Logger log = LoggerFactory.getLogger(EncryptionUtil.class);

    private EncryptionUtil() {
    }

    /**
     * Generates a SHA-256 hash of the input string (Base64-encoded).
     *
     * @param input the string to hash
     * @return Base64-encoded SHA-256 hash
     */
    public static String sha256(String input) {
        if (input == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("Error computing SHA-256 hash: {}", e.getMessage());
            throw new RuntimeException("Hash computation failed", e);
        }
    }

    /**
     * Masks a sensitive value, showing only the first 2 and last 2 characters.
     *
     * @param value the value to mask
     * @return masked string
     */
    public static String mask(String value) {
        if (value == null || value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }
}
