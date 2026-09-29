package com.jagapathi.pharmacy.product.api.request;

import jakarta.validation.constraints.NotNull;

public record UpdateMedicationStatusRequest(
        @NotNull(message = "Active status is required")
        Boolean active
) {
}
