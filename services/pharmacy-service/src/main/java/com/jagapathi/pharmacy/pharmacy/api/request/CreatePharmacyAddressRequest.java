package com.jagapathi.pharmacy.pharmacy.api.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreatePharmacyAddressRequest(
        @NotBlank(message = "Address line 1 is required")
        @Size(min = 5, max = 255, message = "Address line 1 must be between 5 and 255 characters")
        String line1,

        @Size(max = 255, message = "Address line 2 cannot exceed 255 characters")
        String line2,

        @NotBlank(message = "City is required")
        @Size(min = 2, max = 100, message = "City must be between 2 and 100 characters")
        String city,

        @NotBlank(message = "State is required")
        @Size(min = 2, max = 50, message = "State must be between 2 and 50 characters")
        String state,

        @NotBlank(message = "Postal code is required")
        @Size(min = 5, max = 20, message = "Postal code must be between 5 and 20 characters")
        String postalCode,

        @NotNull(message = "Latitude is required")
        @DecimalMin(value = "-90", message = "Latitude must be between -90 and 90")
        @DecimalMax(value = "90", message = "Latitude must be between -90 and 90")
        BigDecimal latitude,

        @NotNull(message = "Longitude is required")
        @DecimalMin(value = "-180", message = "Longitude must be between -180 and 180")
        @DecimalMax(value = "180", message = "Longitude must be between -180 and 180")
        BigDecimal longitude
) {}
