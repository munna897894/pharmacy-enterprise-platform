package com.jagapathi.pharmacy.customer.api.response;

public record CustomerAddressResponse(
        String id,
        String type,
        String line1,
        String line2,
        String city,
        String state,
        String postalCode,
        String country
) {
    public static CustomerAddressResponse from(com.jagapathi.pharmacy.customer.domain.model.CustomerAddress address) {
        return new CustomerAddressResponse(
                address.getId(),
                address.getType(),
                address.getLine1(),
                address.getLine2(),
                address.getCity(),
                address.getState(),
                address.getPostalCode(),
                address.getCountry()
        );
    }
}
