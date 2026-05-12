package org.hkust.ire.common.exception;

/**
 * Exception thrown when IAM authentication or authorization fails.
 *
 * @author ire-team
 * @since 1.0.0
 * @version 1.0.0
 */
public class IamAuthenticationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IamAuthenticationException(String message) {
        super(message);
    }

    public IamAuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
