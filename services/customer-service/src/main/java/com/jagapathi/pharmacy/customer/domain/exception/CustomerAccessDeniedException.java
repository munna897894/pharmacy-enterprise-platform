package com.jagapathi.pharmacy.customer.domain.exception;

public class CustomerAccessDeniedException extends RuntimeException {
    public CustomerAccessDeniedException(String customerId, String userId) {
        super("Access denied to customer " + customerId + " for user " + userId);
    }
}
