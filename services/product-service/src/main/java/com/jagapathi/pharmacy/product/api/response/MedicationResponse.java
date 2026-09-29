package com.jagapathi.pharmacy.product.api.response;

import java.math.BigDecimal;
import java.time.Instant;

public record MedicationResponse(
        String id,
        String ndcCode,
        String name,
        String genericName,
        String manufacturer,
        String dosageForm,
        String strength,
        BigDecimal unitPrice,
        String currency,
        Boolean active,
        Integer version,
        Instant createdAt,
        Instant updatedAt
) {
}
