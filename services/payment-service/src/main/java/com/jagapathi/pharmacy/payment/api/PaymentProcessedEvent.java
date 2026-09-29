package com.jagapathi.pharmacy.payment.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentProcessedEvent(
    UUID eventId,
    String eventType,
    UUID paymentId,
    UUID orderId,
    BigDecimal amount,
    String currency,
    String providerReference,
    String failureCode,
    Instant completedAt,
    Instant failedAt
) {}
