package com.jagapathi.pharmacy.notification.infrastructure.messaging;

import java.time.Instant;
import java.util.UUID;

public record PrescriptionExpiringPayload(
    UUID prescriptionId,
    UUID customerId,
    UUID medicationId,
    String productName,
    Instant expiryDate
) {
}
