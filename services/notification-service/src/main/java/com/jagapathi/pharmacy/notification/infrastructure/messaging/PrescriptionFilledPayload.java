package com.jagapathi.pharmacy.notification.infrastructure.messaging;

import java.time.Instant;
import java.util.UUID;

public record PrescriptionFilledPayload(
    UUID prescriptionId,
    UUID customerId,
    UUID medicationId,
    Integer quantity,
    UUID pharmacyId,
    Instant filledAt
) {
}
