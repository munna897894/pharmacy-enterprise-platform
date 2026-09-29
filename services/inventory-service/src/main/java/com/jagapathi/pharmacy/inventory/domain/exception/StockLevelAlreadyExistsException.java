package com.jagapathi.pharmacy.inventory.domain.exception;

public class StockLevelAlreadyExistsException extends RuntimeException {
    public StockLevelAlreadyExistsException(String pharmacyId, String productId) {
        super("Stock level already exists for pharmacy " + pharmacyId + " and product " + productId);
    }
}
