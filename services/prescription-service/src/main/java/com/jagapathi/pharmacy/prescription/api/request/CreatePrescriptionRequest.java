package com.jagapathi.pharmacy.prescription.api.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CreatePrescriptionRequest(
    @NotBlank(message = "Customer ID is required")
    @Pattern(regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", message = "Invalid customer ID format")
    String customerId,
    
    @NotBlank(message = "Prescriber ID is required")
    @Pattern(regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", message = "Invalid prescriber ID format")
    String prescriberId,
    
    @NotNull(message = "Prescribed at is required")
    Instant prescribedAt,
    
    @NotNull(message = "Expires at is required")
    Instant expiresAt,
    
    @NotEmpty(message = "At least one prescription line is required")
    List<PrescriptionLineCreateRequest> lines
) {
}
