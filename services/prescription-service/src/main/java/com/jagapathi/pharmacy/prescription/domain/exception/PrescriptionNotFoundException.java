package com.jagapathi.pharmacy.prescription.domain.exception;

public class PrescriptionNotFoundException extends RuntimeException {
    public PrescriptionNotFoundException(String id) {
        super("Prescription not found: " + id);
    }
}
