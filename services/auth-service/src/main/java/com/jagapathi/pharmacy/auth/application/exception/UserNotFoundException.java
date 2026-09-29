package com.jagapathi.pharmacy.auth.application.exception;

public class UserNotFoundException extends AuthException {

    public UserNotFoundException(String message) {
        super(message);
    }
}
