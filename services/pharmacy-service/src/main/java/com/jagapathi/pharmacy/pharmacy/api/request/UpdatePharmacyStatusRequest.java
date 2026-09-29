package com.jagapathi.pharmacy.pharmacy.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdatePharmacyStatusRequest(
        @NotBlank(message = "Status is required")
        @Pattern(regexp = "OPEN|CLOSED|DISABLED", message = "Status must be OPEN, CLOSED, or DISABLED")
        String status
) {}
