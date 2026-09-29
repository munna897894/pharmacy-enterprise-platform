package com.jagapathi.pharmacy.product.api.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateMedicationRequest(
        @NotBlank(message = "NDC code is required")
        @Size(min = 5, max = 50, message = "NDC code must be 5-50 characters")
        String ndcCode,

        @NotBlank(message = "Name is required")
        @Size(min = 1, max = 255, message = "Name must be 1-255 characters")
        String name,

        @Size(max = 255, message = "Generic name must be 0-255 characters")
        String genericName,

        @Size(max = 255, message = "Manufacturer must be 0-255 characters")
        String manufacturer,

        @Size(max = 100, message = "Dosage form must be 0-100 characters")
        String dosageForm,

        @Size(max = 100, message = "Strength must be 0-100 characters")
        String strength,

        @NotNull(message = "Unit price is required")
        @DecimalMin(value = "0", inclusive = false, message = "Unit price must be positive")
        @DecimalMax(value = "99999.99", message = "Unit price is too large")
        BigDecimal unitPrice,

        @NotBlank(message = "Currency is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be 3 uppercase letters")
        String currency
) {
}
