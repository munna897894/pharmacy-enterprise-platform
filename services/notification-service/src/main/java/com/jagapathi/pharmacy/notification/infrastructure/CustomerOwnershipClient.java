package com.jagapathi.pharmacy.notification.infrastructure;

import com.jagapathi.pharmacy.notification.application.CustomerOwnershipLookupException;
import com.jagapathi.pharmacy.notification.application.CustomerOwnershipVerifier;
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

import java.util.List;
import java.util.UUID;

@Component
public class CustomerOwnershipClient implements CustomerOwnershipVerifier {

    private final RestTemplate restTemplate;
    private final String customerBaseUrl;

    public CustomerOwnershipClient(
        RestTemplate restTemplate,
        @Value("${customer.service.base-url:http://customer-service:8083}") String customerBaseUrl
    ) {
        this.restTemplate = restTemplate;
        this.customerBaseUrl = customerBaseUrl.replaceAll("/+$", "");
    }

    @Override
    public boolean isOwner(UUID customerId, Jwt callerJwt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(callerJwt.getTokenValue());
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        try {
            restTemplate.exchange(
                customerBaseUrl + "/api/v1/customers/" + customerId,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Void.class
            );
            return true;
        } catch (HttpClientErrorException.Forbidden | HttpClientErrorException.NotFound exception) {
            return false;
        } catch (RestClientException exception) {
            throw new CustomerOwnershipLookupException(exception);
        }
    }
}
