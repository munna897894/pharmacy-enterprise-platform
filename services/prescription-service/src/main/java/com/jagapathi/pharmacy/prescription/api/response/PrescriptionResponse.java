package com.jagapathi.pharmacy.prescription.api.response;

import java.time.Instant;
import java.util.List;

public record PrescriptionResponse(
    String id,
    String customerId,
    String prescriberId,
    Instant prescribedAt,
    Instant expiresAt,
    String status,
    Integer version,
    Instant createdAt,
    Instant updatedAt,
    List<PrescriptionLineResponse> lines
) {
}
