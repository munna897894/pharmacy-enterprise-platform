package com.jagapathi.pharmacy.notification.infrastructure.messaging;

import java.util.UUID;

public record OrderCreatedPayload(
    UUID orderId,
    UUID customerId,
    UUID prescriptionId,
    UUID pharmacyId,
    String currency,
    java.math.BigDecimal total,
    java.util.List<OrderItem> items
) {
    public record OrderItem(UUID medicationId, Integer quantity, java.math.BigDecimal unitPrice) {}
}
