package org.hkust.ire.common.exception;

/**
 * Exception thrown when incoming payload fails validation.
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
public class InvalidPayloadException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidPayloadException(String message) {
        super(message);
    }

    public InvalidPayloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
