package com.jagapathi.pharmacy.payment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RefundRequest(
    @NotNull(message = "reason must not be null")
    @NotBlank(message = "reason must not be blank")
    String reason
) {}
