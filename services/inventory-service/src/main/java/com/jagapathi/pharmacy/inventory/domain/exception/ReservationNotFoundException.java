package com.jagapathi.pharmacy.inventory.domain.exception;

public class ReservationNotFoundException extends RuntimeException {
    public ReservationNotFoundException(String orderId) {
        super("Inventory reservation not found for order: " + orderId);
    }
}
