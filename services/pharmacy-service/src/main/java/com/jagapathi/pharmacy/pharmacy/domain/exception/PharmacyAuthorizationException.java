package com.jagapathi.pharmacy.pharmacy.domain.exception;

public class PharmacyAuthorizationException extends RuntimeException {
    public PharmacyAuthorizationException(String userId) {
        super("User " + userId + " is not authorized to perform this action");
    }
}
