package org.hkust.ire.common.utils;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

/**
 * General utility methods for the IRE system.
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
public final class GeneralUtil {

    private static final Logger log = LoggerFactory.getLogger(GeneralUtil.class);

    private GeneralUtil() {
    }

    /**
     * Generates a new UUID string.
     *
     * @return UUID string
     */
    public static String generateUuid() {
        return UUID.randomUUID().toString();
    }

    /**
     * Normalizes a string by trimming and lower-casing.
     *
     * @param input the input string
     * @return normalized string, or empty string if null/blank
     */
    public static String normalize(String input) {
        if (StringUtils.isBlank(input)) {
            return "";
        }
        return input.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    /**
     * Checks if a value is null or blank.
     *
     * @param value the string to check
     * @return true if null or blank
     */
    public static boolean isNullOrBlank(String value) {
        return StringUtils.isBlank(value);
    }

    /**
     * Safely trims a string, returning null if input is null.
     *
     * @param value the string to trim
     * @return trimmed string or null
     */
    public static String safeTrim(String value) {
        if (value == null) {
            return null;
        }
        return value.trim();
    }
}
