package com.jagapathi.pharmacy.order.domain;

public enum OrderStatus {
    CREATED,
    INVENTORY_PENDING,
    INVENTORY_RESERVED,
    PAYMENT_PENDING,
    CONFIRMED,
    READY_FOR_PICKUP,
    COMPLETED,
    CANCELLED_INVENTORY,
    CANCELLED_PAYMENT
}
