package com.jagapathi.pharmacy.order.infrastructure.client;

public class InventoryAvailabilityException extends RuntimeException {
    public InventoryAvailabilityException(String message, Throwable cause) {
        super(message, cause);
    }
}
