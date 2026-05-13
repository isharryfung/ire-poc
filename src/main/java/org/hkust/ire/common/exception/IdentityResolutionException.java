package org.hkust.ire.common.exception;

/**
 * Base exception for identity resolution failures.
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
public class IdentityResolutionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * @param message detail message
     */
    public IdentityResolutionException(String message) {
        super(message);
    }

    /**
     * @param message detail message
     * @param cause   root cause
     */
    public IdentityResolutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
