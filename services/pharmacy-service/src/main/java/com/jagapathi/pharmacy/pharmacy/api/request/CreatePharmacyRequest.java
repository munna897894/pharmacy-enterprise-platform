package com.jagapathi.pharmacy.pharmacy.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePharmacyRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 255, message = "Name must be between 2 and 255 characters")
        String name,

        @NotBlank(message = "License number is required")
        @Size(min = 5, max = 100, message = "License number must be between 5 and 100 characters")
        String licenseNumber,

        @NotBlank(message = "Phone is required")
        @Size(min = 10, max = 20, message = "Phone must be between 10 and 20 characters")
        String phone,

        @NotBlank(message = "Timezone is required")
        String timezone
) {}
