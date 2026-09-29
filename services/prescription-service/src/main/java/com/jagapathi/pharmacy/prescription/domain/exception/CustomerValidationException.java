package com.jagapathi.pharmacy.prescription.domain.exception;

public class CustomerValidationException extends RuntimeException {
    public CustomerValidationException(String message) {
        super(message);
    }
}
