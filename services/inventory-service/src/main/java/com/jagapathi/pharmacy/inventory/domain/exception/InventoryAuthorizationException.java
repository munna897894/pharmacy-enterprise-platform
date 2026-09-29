package com.jagapathi.pharmacy.inventory.domain.exception;

public class InventoryAuthorizationException extends RuntimeException {
    public InventoryAuthorizationException(String userId) {
        super("User " + userId + " is not authorized to perform this action");
    }
}
