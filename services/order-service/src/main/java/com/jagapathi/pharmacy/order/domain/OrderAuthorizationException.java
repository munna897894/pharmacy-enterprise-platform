package com.jagapathi.pharmacy.order.domain;

import java.util.UUID;

public class OrderAuthorizationException extends RuntimeException {
    public OrderAuthorizationException(String message) {
        super(message);
    }

    public OrderAuthorizationException(UUID customerId, UUID orderId) {
        super("Customer " + customerId + " is not authorized to access order " + orderId);
    }
}
