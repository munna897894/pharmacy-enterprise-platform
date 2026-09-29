package com.jagapathi.pharmacy.payment.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentRefundedEvent(
    UUID eventId,
    String eventType,
    String aggregateType,
    UUID aggregateId,
    Instant occurredAt,
    String producer,
    UUID correlationId,
    UUID paymentId,
    UUID orderId,
    BigDecimal refundAmount
) {}
