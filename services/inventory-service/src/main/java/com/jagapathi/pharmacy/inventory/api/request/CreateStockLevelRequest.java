package com.jagapathi.pharmacy.inventory.api.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CreateStockLevelRequest(
        @NotBlank(message = "Pharmacy ID is required")
        String pharmacyId,

        @NotBlank(message = "Product ID is required")
        String productId,

        @NotNull(message = "Quantity on hand is required")
        @DecimalMin(value = "0", message = "Quantity on hand must be >= 0")
        BigDecimal quantityOnHand,

        @NotNull(message = "Reorder level is required")
        @DecimalMin(value = "0", message = "Reorder level must be >= 0")
        BigDecimal reorderLevel,

        @NotNull(message = "Reorder quantity is required")
        @DecimalMin(value = "1", message = "Reorder quantity must be > 0")
        BigDecimal reorderQuantity
) {}
