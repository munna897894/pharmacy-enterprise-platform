package com.jagapathi.pharmacy.payment.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagapathi.pharmacy.payment.domain.GatewayException;
import com.jagapathi.pharmacy.payment.domain.PaymentMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Component
public class PaymentGatewayClient {
    
    private final RestTemplate restTemplate;
    private final String gatewayBaseUrl;
    private final ObjectMapper objectMapper;
    
    public PaymentGatewayClient(RestTemplate restTemplate,
                                @Value("${payment.gateway.base-url:http://external-mock-service:8080}") String gatewayBaseUrl,
                                ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.gatewayBaseUrl = gatewayBaseUrl;
        this.objectMapper = objectMapper;
    }
    
    public GatewayResponse processPayment(BigDecimal amount, String currency, PaymentMethod paymentMethod, String reference) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("amount", amount);
            request.put("currency", currency);
            request.put("paymentMethod", paymentMethod.name());
            request.put("reference", reference);
            
            String url = gatewayBaseUrl + "/api/v1/mock/process-payment";
            
            GatewayResponse response = restTemplate.postForObject(url, request, GatewayResponse.class);
            
            if (response == null) {
                throw new GatewayException("Empty response from payment gateway", "EMPTY_RESPONSE", false);
            }
            
            return response;
        } catch (RestClientException e) {
            throw new GatewayException("Failed to connect to payment gateway: " + e.getMessage(), "GATEWAY_CONNECTION_ERROR", true);
        }
    }
    
    public static class GatewayResponse {
        public boolean success;
        public String transactionId;
        public String message;
        
        public GatewayResponse() {}
        
        public GatewayResponse(boolean success, String transactionId, String message) {
            this.success = success;
            this.transactionId = transactionId;
            this.message = message;
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public void setSuccess(boolean success) {
            this.success = success;
        }
        
        public String getTransactionId() {
            return transactionId;
        }
        
        public void setTransactionId(String transactionId) {
            this.transactionId = transactionId;
        }
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
    }
}
