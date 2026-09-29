package com.jagapathi.pharmacy.order.domain;

/**
 * Thrown when a client reuses an Idempotency-Key with a request body that does
 * not match the original request the key was first associated with.
 */
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
