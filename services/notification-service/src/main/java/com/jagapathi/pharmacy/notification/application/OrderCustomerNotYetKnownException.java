package com.jagapathi.pharmacy.notification.application;

import java.util.UUID;

/**
 * Raised when a payment event arrives before the matching OrderCreated has been projected.
 * Propagates to the binder so the delivery is retried and eventually dead-lettered.
 */
public class OrderCustomerNotYetKnownException extends RuntimeException {

    public OrderCustomerNotYetKnownException(UUID orderId) {
        super("No customer projection yet for order " + orderId);
    }
}
