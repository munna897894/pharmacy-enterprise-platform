package com.jagapathi.pharmacy.auth.application.exception;

public class UserAlreadyExistsException extends AuthException {

    public UserAlreadyExistsException(String message) {
        super(message);
    }
}
