package com.jagapathi.pharmacy.payment.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagapathi.pharmacy.payment.api.PaymentResponse;
import com.jagapathi.pharmacy.payment.domain.PaymentMethod;
import com.jagapathi.pharmacy.payment.domain.Payment;
import com.jagapathi.pharmacy.payment.domain.DuplicatePaymentException;
import com.jagapathi.pharmacy.payment.infrastructure.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceOrderLookupTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private OutboxRepository outboxRepository;
    @Mock private PaymentGatewayClient gatewayClient;
    @Mock private OrderLookupClient orderLookupClient;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;
    @Mock private ObjectMapper objectMapper;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, outboxRepository, gatewayClient,
            orderLookupClient, kafkaTemplate, objectMapper);
    }

    @Test
    void processPaymentRejectsWhenAmountDiffersFromOrderTotal() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(paymentRepository.findByIdempotencyKey("idem-1")).thenReturn(Optional.empty());
        when(orderLookupClient.getOrderSummary(orderId))
            .thenReturn(new OrderSummaryResponse(orderId, customerId, new BigDecimal("25.00"), "USD", "CONFIRMED"));

        assertThatThrownBy(() -> paymentService.processPayment(
            orderId, customerId, new BigDecimal("20.00"), "USD", PaymentMethod.CREDIT_CARD, "idem-1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("does not match order total");
    }

    @Test
    void processPaymentRejectsWhenCustomerDoesNotOwnOrder() {
        UUID orderId = UUID.randomUUID();
        UUID requestedCustomerId = UUID.randomUUID();
        UUID orderCustomerId = UUID.randomUUID();
        when(paymentRepository.findByIdempotencyKey("idem-2")).thenReturn(Optional.empty());
        when(orderLookupClient.getOrderSummary(orderId))
            .thenReturn(new OrderSummaryResponse(orderId, orderCustomerId, new BigDecimal("25.00"), "USD", "CONFIRMED"));

        assertThatThrownBy(() -> paymentService.processPayment(
            orderId, requestedCustomerId, new BigDecimal("25.00"), "USD", PaymentMethod.CREDIT_CARD, "idem-2"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Customer does not match the order");
    }

    @Test
    void processPaymentRejectsIdempotencyKeyReusedForAnotherCustomerOrOrder() {
        UUID existingOrderId = UUID.randomUUID();
        UUID existingCustomerId = UUID.randomUUID();
        Payment existingPayment = new Payment.Builder()
            .orderId(existingOrderId)
            .customerId(existingCustomerId)
            .amount(new BigDecimal("25.00"))
            .currency("USD")
            .paymentMethod(PaymentMethod.CREDIT_CARD)
            .idempotencyKey("idem-3")
            .build();
        when(paymentRepository.findByIdempotencyKey("idem-3")).thenReturn(Optional.of(existingPayment));

        assertThatThrownBy(() -> paymentService.processPayment(
            UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("25.00"), "USD",
            PaymentMethod.CREDIT_CARD, "idem-3"))
            .isInstanceOf(DuplicatePaymentException.class)
            .hasMessageContaining("already used for another payment");
    }
}
