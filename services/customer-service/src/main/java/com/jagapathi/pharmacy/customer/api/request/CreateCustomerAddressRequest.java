package com.jagapathi.pharmacy.customer.api.request;

import jakarta.validation.constraints.*;

public record CreateCustomerAddressRequest(
        @NotBlank(message = "Type is required")
        @Pattern(regexp = "^(HOME|WORK|BILLING|SHIPPING)$", message = "Type must be HOME, WORK, BILLING, or SHIPPING")
        String type,

        @NotBlank(message = "Line1 is required")
        @Size(min = 1, max = 255, message = "Line1 must be 1-255 characters")
        String line1,

        @Size(max = 255, message = "Line2 must be 0-255 characters")
        String line2,

        @NotBlank(message = "City is required")
        @Size(min = 1, max = 100, message = "City must be 1-100 characters")
        String city,

        @NotBlank(message = "State is required")
        @Size(min = 1, max = 50, message = "State must be 1-50 characters")
        String state,

        @NotBlank(message = "Postal code is required")
        @Size(min = 1, max = 20, message = "Postal code must be 1-20 characters")
        String postalCode,

        @NotBlank(message = "Country is required")
        @Size(min = 1, max = 100, message = "Country must be 1-100 characters")
        String country
) {}
