package com.jagapathi.pharmacy.auth.application.exception;

public class InvalidTokenException extends AuthException {

    public InvalidTokenException(String message) {
        super(message);
    }

    public InvalidTokenException(String message, Throwable cause) {
        super(message, cause);
    }
}
