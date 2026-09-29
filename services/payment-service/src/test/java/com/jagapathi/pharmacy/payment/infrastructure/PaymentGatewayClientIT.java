package com.jagapathi.pharmacy.payment.infrastructure;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.jagapathi.pharmacy.payment.domain.GatewayException;
import com.jagapathi.pharmacy.payment.domain.PaymentMethod;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.net.http.HttpTimeoutException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.*;

class PaymentGatewayClientIT {
    
    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort())
        .build();
    
    private PaymentGatewayClient paymentGatewayClient;
    private RestTemplate restTemplate;
    
    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplateConfig().restTemplate(new RestTemplateBuilder());
        paymentGatewayClient = new PaymentGatewayClient(
            restTemplate,
            wm.baseUrl(),
            new ObjectMapper()
        );
    }
    
    @Test
    void shouldSuccessfullyProcessPayment() {
        wm.stubFor(post(urlEqualTo("/api/v1/mock/process-payment"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"success\":true,\"transactionId\":\"TXN-12345\",\"message\":\"Success\"}")
                .withStatus(200)));
        
        PaymentGatewayClient.GatewayResponse response = paymentGatewayClient.processPayment(
            BigDecimal.valueOf(100.00),
            "USD",
            PaymentMethod.CREDIT_CARD,
            "PAY-ref-123"
        );
        
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getTransactionId()).isEqualTo("TXN-12345");
        assertThat(response.getMessage()).isEqualTo("Success");
    }
    
    @Test
    void shouldHandleGatewayFailure() {
        wm.stubFor(post(urlEqualTo("/api/v1/mock/process-payment"))
            .willReturn(aResponse()
                .withHeader("Content-Type", "application/json")
                .withBody("{\"success\":false,\"transactionId\":null,\"message\":\"Card declined\"}")
                .withStatus(200)));
        
        PaymentGatewayClient.GatewayResponse response = paymentGatewayClient.processPayment(
            BigDecimal.valueOf(100.00),
            "USD",
            PaymentMethod.CREDIT_CARD,
            "PAY-ref-123"
        );
        
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getMessage()).isEqualTo("Card declined");
    }
    
    @Test
    void shouldHandleGatewayConnectionError() {
        wm.stubFor(post(urlEqualTo("/api/v1/mock/process-payment"))
            .willReturn(aResponse()
                .withStatus(500)
                .withBody("Internal Server Error")));
        
        assertThatThrownBy(() -> paymentGatewayClient.processPayment(
            BigDecimal.valueOf(100.00),
            "USD",
            PaymentMethod.CREDIT_CARD,
            "PAY-ref-123"
        )).isInstanceOf(GatewayException.class);
    }
    
    @Test
    void shouldHandleGatewayTimeout() {
        wm.stubFor(post(urlEqualTo("/api/v1/mock/process-payment"))
            .willReturn(aResponse()
                .withFixedDelay(10000)
                .withStatus(200)));
        
        assertThatThrownBy(() -> paymentGatewayClient.processPayment(
            BigDecimal.valueOf(100.00),
            "USD",
            PaymentMethod.CREDIT_CARD,
            "PAY-ref-123"
        )).isInstanceOf(GatewayException.class);
    }
    
    @Test
    void shouldRetryableOnConnectionError() {
        wm.stubFor(post(urlEqualTo("/api/v1/mock/process-payment"))
            .willReturn(aResponse()
                .withStatus(503)
                .withBody("Service Unavailable")));
        
        assertThatThrownBy(() -> paymentGatewayClient.processPayment(
            BigDecimal.valueOf(100.00),
            "USD",
            PaymentMethod.CREDIT_CARD,
            "PAY-ref-123"
        )).isInstanceOf(GatewayException.class)
            .extracting(e -> ((GatewayException) e).isRetryable())
            .isEqualTo(true);
    }
}
