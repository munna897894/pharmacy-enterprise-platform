package com.jagapathi.pharmacy.prescription.domain.exception;

public class PrescriberValidationException extends RuntimeException {
    public PrescriberValidationException(String message) {
        super(message);
    }
}
