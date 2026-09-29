package com.jagapathi.pharmacy.prescription.api.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record FillPrescriptionLineRequest(
    @NotNull(message = "Dispensed quantity is required")
    @DecimalMin(value = "0.0001", message = "Dispensed quantity must be greater than 0")
    @DecimalMax(value = "9999.9999", message = "Dispensed quantity must not exceed 9999.9999")
    @Digits(integer = 5, fraction = 4, message = "Dispensed quantity must have at most 5 integer digits and 4 decimal places")
    BigDecimal dispensedQuantity
) {
}
