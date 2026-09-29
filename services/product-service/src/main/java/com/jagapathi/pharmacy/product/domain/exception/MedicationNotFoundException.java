package com.jagapathi.pharmacy.product.domain.exception;

public class MedicationNotFoundException extends RuntimeException {
    public MedicationNotFoundException(String id) {
        super("Medication not found: " + id);
    }
}
