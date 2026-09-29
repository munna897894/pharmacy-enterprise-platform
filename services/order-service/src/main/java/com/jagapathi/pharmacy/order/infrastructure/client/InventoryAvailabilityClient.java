package com.jagapathi.pharmacy.order.infrastructure.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.util.UUID;

@Component
public class InventoryAvailabilityClient {

    private final RestTemplate restTemplate;
    private final String inventoryBaseUrl;

    public InventoryAvailabilityClient(RestTemplate restTemplate,
                                      @Value("${inventory.service.base-url:http://inventory-service:8085}") String inventoryBaseUrl) {
        this.restTemplate = restTemplate;
        this.inventoryBaseUrl = inventoryBaseUrl;
    }

    public InventoryAvailabilityResponse checkAvailability(UUID pharmacyId, UUID medicationId, BigDecimal quantity) {
        URI url = UriComponentsBuilder.fromHttpUrl(inventoryBaseUrl + "/api/v1/inventory/availability")
            .queryParam("pharmacyId", pharmacyId)
            .queryParam("medicationId", medicationId)
            .queryParamIfPresent("quantity", java.util.Optional.ofNullable(quantity))
            .build()
            .toUri();
        try {
            ResponseEntity<InventoryAvailabilityResponse> response = restTemplate.getForEntity(url, InventoryAvailabilityResponse.class);
            InventoryAvailabilityResponse body = response.getBody();
            if (body == null) {
                return new InventoryAvailabilityResponse(false, "Inventory response was empty");
            }
            return body;
        } catch (RestClientException ex) {
            throw new InventoryAvailabilityException("Failed to check inventory availability", ex);
        }
    }
}
