package com.jagapathi.pharmacy.inventory.api.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record AdjustStockRequest(
        @NotNull(message = "Quantity is required")
        @DecimalMin(value = "0.01", message = "Quantity must be > 0")
        BigDecimal quantity,

        @NotBlank(message = "Adjustment type is required")
        @Pattern(regexp = "PURCHASE|SALE|RETURN|DAMAGE|ADJUSTMENT", message = "Adjustment type must be one of: PURCHASE, SALE, RETURN, DAMAGE, ADJUSTMENT")
        String adjustmentType,

        @Size(max = 255, message = "Reason cannot exceed 255 characters")
        String reason
) {}
