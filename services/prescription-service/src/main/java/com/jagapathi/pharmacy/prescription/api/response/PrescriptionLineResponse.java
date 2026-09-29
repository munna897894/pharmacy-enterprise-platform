package com.jagapathi.pharmacy.prescription.api.response;

import java.math.BigDecimal;
import java.time.Instant;

public record PrescriptionLineResponse(
    String id,
    String prescriptionId,
    String productId,
    BigDecimal quantity,
    String instructions,
    BigDecimal dispensedQuantity,
    Instant filledAt,
    Instant createdAt
) {
}
