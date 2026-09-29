package com.jagapathi.pharmacy.payment.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
    UUID id,
    UUID orderId,
    UUID customerId,
    BigDecimal amount,
    String currency,
    String paymentMethod,
    String status,
    String referenceNumber,
    Instant createdAt,
    Instant updatedAt
) {}
