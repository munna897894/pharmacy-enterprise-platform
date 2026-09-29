package com.jagapathi.pharmacy.notification.infrastructure.messaging;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentProcessedPayload(
    UUID paymentId,
    UUID orderId,
    UUID customerId,
    BigDecimal amount,
    String currency,
    String providerReference,
    Instant completedAt
) {
}
