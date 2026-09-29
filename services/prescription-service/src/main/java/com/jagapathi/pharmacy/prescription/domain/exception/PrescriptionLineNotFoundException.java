package com.jagapathi.pharmacy.prescription.domain.exception;

public class PrescriptionLineNotFoundException extends RuntimeException {
    public PrescriptionLineNotFoundException(String id) {
        super("Prescription line not found: " + id);
    }
}
