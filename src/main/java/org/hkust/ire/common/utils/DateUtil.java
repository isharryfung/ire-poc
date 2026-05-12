package org.hkust.ire.common.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Date utility methods for the IRE system.
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
public final class DateUtil {

    private static final Logger log = LoggerFactory.getLogger(DateUtil.class);
    private static final String DEFAULT_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private DateUtil() {
    }

    /**
     * Formats a date using the default pattern (yyyy-MM-dd HH:mm:ss).
     *
     * @param date the date to format
     * @return formatted string or empty string if null
     */
    public static String format(Date date) {
        if (date == null) {
            return "";
        }
        return new SimpleDateFormat(DEFAULT_FORMAT).format(date);
    }

    /**
     * Formats a date with a custom pattern.
     *
     * @param date    the date
     * @param pattern the format pattern
     * @return formatted string
     */
    public static String format(Date date, String pattern) {
        if (date == null) {
            return "";
        }
        try {
            return new SimpleDateFormat(pattern).format(date);
        } catch (Exception e) {
            log.error("Error formatting date with pattern {}: {}", pattern, e.getMessage());
            return "";
        }
    }

    /**
     * Returns the current date/time.
     *
     * @return current Date
     */
    public static Date now() {
        return new Date();
    }
}
