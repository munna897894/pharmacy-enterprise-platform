package com.jagapathi.pharmacy.customer.api.response;

import java.time.Instant;
import java.time.LocalDate;

public record CustomerResponse(
        String id,
        String firstName,
        String lastName,
        String email,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Integer version
) {
    public static CustomerResponse from(com.jagapathi.pharmacy.customer.domain.model.Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getFirstName(),
                customer.getLastName(),
                customer.getEmail(),
                customer.getStatus(),
                customer.getCreatedAt(),
                customer.getUpdatedAt(),
                customer.getVersion()
        );
    }
}
