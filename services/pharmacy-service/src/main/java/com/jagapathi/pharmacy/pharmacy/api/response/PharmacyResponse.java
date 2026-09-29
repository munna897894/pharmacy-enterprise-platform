package com.jagapathi.pharmacy.pharmacy.api.response;

import com.jagapathi.pharmacy.pharmacy.domain.model.Pharmacy;
import java.time.Instant;
import java.util.List;

public record PharmacyResponse(
        String id,
        String name,
        String licenseNumber,
        String phone,
        String status,
        String timezone,
        Instant createdAt,
        Instant updatedAt,
        PharmacyAddressResponse address,
        List<BusinessHourResponse> businessHours
) {
    public static PharmacyResponse from(Pharmacy pharmacy, PharmacyAddressResponse address, List<BusinessHourResponse> businessHours) {
        return new PharmacyResponse(
                pharmacy.getId(),
                pharmacy.getName(),
                pharmacy.getLicenseNumber(),
                pharmacy.getPhone(),
                pharmacy.getStatus(),
                pharmacy.getTimezone(),
                pharmacy.getCreatedAt(),
                pharmacy.getUpdatedAt(),
                address,
                businessHours
        );
    }
}
