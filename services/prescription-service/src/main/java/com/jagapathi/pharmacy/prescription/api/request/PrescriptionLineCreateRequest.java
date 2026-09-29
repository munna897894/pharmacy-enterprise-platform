package com.jagapathi.pharmacy.prescription.api.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record PrescriptionLineCreateRequest(
    @NotBlank(message = "Product ID is required")
    @Pattern(regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", message = "Invalid product ID format")
    String productId,
    
    @NotNull(message = "Quantity is required")
    @DecimalMin(value = "0.0001", message = "Quantity must be greater than 0")
    @DecimalMax(value = "9999.9999", message = "Quantity must not exceed 9999.9999")
    @Digits(integer = 5, fraction = 4, message = "Quantity must have at most 5 integer digits and 4 decimal places")
    BigDecimal quantity,
    
    String instructions
) {
}
