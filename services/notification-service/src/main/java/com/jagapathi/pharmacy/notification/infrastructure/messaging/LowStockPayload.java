package com.jagapathi.pharmacy.notification.infrastructure.messaging;

import java.util.UUID;

public record LowStockPayload(
    UUID medicationId,
    UUID pharmacyId,
    String medicationName,
    Integer currentStock,
    Integer reorderLevel
) {
}
