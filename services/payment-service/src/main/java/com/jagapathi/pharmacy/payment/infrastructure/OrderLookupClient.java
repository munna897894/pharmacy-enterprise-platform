package com.jagapathi.pharmacy.payment.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.UUID;

@Component
public class OrderLookupClient {

    private final RestTemplate restTemplate;
    private final String orderBaseUrl;

    public OrderLookupClient(RestTemplate restTemplate,
                             @Value("${order.service.base-url:http://order-service:8087}") String orderBaseUrl) {
        this.restTemplate = restTemplate;
        this.orderBaseUrl = orderBaseUrl;
    }

    public OrderSummaryResponse getOrderSummary(UUID orderId) {
        try {
            String url = orderBaseUrl + "/api/v1/orders/" + orderId;
            return restTemplate.getForObject(url, OrderSummaryResponse.class);
        } catch (RestClientException ex) {
            throw new OrderLookupException("Failed to load order " + orderId, ex);
        }
    }
}
