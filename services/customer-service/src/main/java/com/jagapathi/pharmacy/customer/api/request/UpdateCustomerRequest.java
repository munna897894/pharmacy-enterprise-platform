package com.jagapathi.pharmacy.customer.api.request;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record UpdateCustomerRequest(
        @NotBlank(message = "First name is required")
        @Size(min = 1, max = 100, message = "First name must be 1-100 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(min = 1, max = 100, message = "Last name must be 1-100 characters")
        String lastName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        String email,

        @Size(max = 20, message = "Phone must be 0-20 characters")
        String phone,

        LocalDate dateOfBirth
) {}
