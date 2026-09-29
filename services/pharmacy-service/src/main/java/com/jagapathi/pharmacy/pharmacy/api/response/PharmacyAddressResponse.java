package com.jagapathi.pharmacy.pharmacy.api.response;

import com.jagapathi.pharmacy.pharmacy.domain.model.PharmacyAddress;
import java.math.BigDecimal;

public record PharmacyAddressResponse(
        String id,
        String line1,
        String line2,
        String city,
        String state,
        String postalCode,
        BigDecimal latitude,
        BigDecimal longitude
) {
    public static PharmacyAddressResponse from(PharmacyAddress address) {
        return new PharmacyAddressResponse(
                address.getId(),
                address.getLine1(),
                address.getLine2(),
                address.getCity(),
                address.getState(),
                address.getPostalCode(),
                address.getLatitude(),
                address.getLongitude()
        );
    }
}
