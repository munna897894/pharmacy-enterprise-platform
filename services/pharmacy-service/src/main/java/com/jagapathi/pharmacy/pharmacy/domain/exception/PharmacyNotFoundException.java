package com.jagapathi.pharmacy.pharmacy.domain.exception;

public class PharmacyNotFoundException extends RuntimeException {
    public PharmacyNotFoundException(String pharmacyId) {
        super("Pharmacy not found: " + pharmacyId);
    }
}
