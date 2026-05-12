package com.university.ire.exception;

public class IamAuthenticationException extends RuntimeException {
    public IamAuthenticationException(String message) {
        super(message);
    }
}
