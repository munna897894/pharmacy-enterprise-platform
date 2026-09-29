package com.jagapathi.pharmacy.auth.application.exception;

public class InvalidCredentialsException extends AuthException {

    public InvalidCredentialsException(String message) {
        super(message);
    }
}
