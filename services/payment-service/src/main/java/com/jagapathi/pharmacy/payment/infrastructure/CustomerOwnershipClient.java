package com.jagapathi.pharmacy.payment.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Component
public class CustomerOwnershipClient {

    private final RestTemplate restTemplate;
    private final String customerBaseUrl;

    public CustomerOwnershipClient(
        RestTemplate restTemplate,
        @Value("${customer.service.base-url:http://customer-service:8083}") String customerBaseUrl
    ) {
        this.restTemplate = restTemplate;
        this.customerBaseUrl = customerBaseUrl.replaceAll("/+$", "");
    }

    public boolean isOwner(UUID customerId, Jwt jwt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt.getTokenValue());
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        try {
            restTemplate.exchange(
                customerBaseUrl + "/api/v1/customers/" + customerId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Object.class
            );
            return true;
        } catch (HttpClientErrorException.Forbidden | HttpClientErrorException.NotFound exception) {
            return false;
        } catch (RestClientException exception) {
            throw new CustomerOwnershipLookupException(exception);
        }
    }
}
