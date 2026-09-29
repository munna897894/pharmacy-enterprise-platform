package com.jagapathi.pharmacy.notification.application;

public class CustomerOwnershipLookupException extends RuntimeException {

    public CustomerOwnershipLookupException(Throwable cause) {
        super("Customer ownership could not be verified", cause);
    }
}
