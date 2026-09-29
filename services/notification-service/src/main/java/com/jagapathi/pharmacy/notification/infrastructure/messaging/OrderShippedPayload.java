package com.jagapathi.pharmacy.notification.infrastructure.messaging;

import java.time.Instant;
import java.util.UUID;

public record OrderShippedPayload(
    UUID orderId,
    UUID customerId,
    UUID pharmacyId,
    Instant shippedAt
) {
}
