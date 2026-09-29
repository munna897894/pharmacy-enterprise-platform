package com.jagapathi.pharmacy.payment.infrastructure;

public class OrderLookupException extends RuntimeException {
    public OrderLookupException(String message, Throwable cause) {
        super(message, cause);
    }
}
