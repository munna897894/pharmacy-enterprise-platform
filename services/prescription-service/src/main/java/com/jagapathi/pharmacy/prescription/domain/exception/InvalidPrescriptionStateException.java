package com.jagapathi.pharmacy.prescription.domain.exception;

public class InvalidPrescriptionStateException extends RuntimeException {
    public InvalidPrescriptionStateException(String message) {
        super(message);
    }
}
