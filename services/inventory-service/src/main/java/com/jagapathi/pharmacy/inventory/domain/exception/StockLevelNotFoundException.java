package com.jagapathi.pharmacy.inventory.domain.exception;

public class StockLevelNotFoundException extends RuntimeException {
    public StockLevelNotFoundException(String stockLevelId) {
        super("Stock level not found: " + stockLevelId);
    }
}
