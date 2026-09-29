package com.jagapathi.pharmacy.pharmacy.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePharmacyRequest(
        @NotBlank(message = "Name is required")
        @Size(min = 2, max = 255, message = "Name must be between 2 and 255 characters")
        String name,

        @NotBlank(message = "Phone is required")
        @Size(min = 10, max = 20, message = "Phone must be between 10 and 20 characters")
        String phone
) {}
