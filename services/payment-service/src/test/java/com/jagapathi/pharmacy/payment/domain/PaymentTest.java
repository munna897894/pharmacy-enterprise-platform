package com.jagapathi.pharmacy.payment.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

class PaymentTest {
    
    @Test
    void shouldCreatePaymentWithBuilder() {
        UUID id = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        
        Payment payment = new Payment.Builder()
            .id(id)
            .orderId(orderId)
            .customerId(customerId)
            .amount(BigDecimal.valueOf(100.00))
            .currency("USD")
            .paymentMethod(PaymentMethod.CREDIT_CARD)
            .status(PaymentStatus.PENDING)
            .idempotencyKey(idempotencyKey)
            .build();
        
        assertThat(payment.getId()).isEqualTo(id);
        assertThat(payment.getOrderId()).isEqualTo(orderId);
        assertThat(payment.getCustomerId()).isEqualTo(customerId);
        assertThat(payment.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(100.00));
        assertThat(payment.getCurrency()).isEqualTo("USD");
        assertThat(payment.getPaymentMethod()).isEqualTo(PaymentMethod.CREDIT_CARD);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getIdempotencyKey()).isEqualTo(idempotencyKey);
    }
    
    @Test
    void shouldGenerateIdWhenNotProvidedInBuilder() {
        Payment payment = new Payment.Builder()
            .orderId(UUID.randomUUID())
            .customerId(UUID.randomUUID())
            .amount(BigDecimal.valueOf(50.00))
            .currency("EUR")
            .paymentMethod(PaymentMethod.DEBIT_CARD)
            .idempotencyKey(UUID.randomUUID().toString())
            .build();
        
        assertThat(payment.getId()).isNotNull();
    }
    
    @Test
    void shouldSetTimestampsAutomatically() {
        Instant before = Instant.now();
        
        Payment payment = new Payment.Builder()
            .orderId(UUID.randomUUID())
            .customerId(UUID.randomUUID())
            .amount(BigDecimal.valueOf(75.50))
            .currency("GBP")
            .paymentMethod(PaymentMethod.DIGITAL_WALLET)
            .idempotencyKey(UUID.randomUUID().toString())
            .build();
        
        Instant after = Instant.now();
        
        assertThat(payment.getCreatedAt()).isBetween(before, after);
        assertThat(payment.getUpdatedAt()).isBetween(before, after);
    }
    
    @Test
    void shouldUpdatePaymentStatus() {
        Payment payment = new Payment.Builder()
            .orderId(UUID.randomUUID())
            .customerId(UUID.randomUUID())
            .amount(BigDecimal.valueOf(100.00))
            .currency("USD")
            .paymentMethod(PaymentMethod.CREDIT_CARD)
            .status(PaymentStatus.PENDING)
            .idempotencyKey(UUID.randomUUID().toString())
            .build();
        
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        
        payment.setStatus(PaymentStatus.PROCESSING);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PROCESSING);
        
        payment.setStatus(PaymentStatus.SUCCESS);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }
    
    @Test
    void shouldSetReferenceNumber() {
        Payment payment = new Payment.Builder()
            .orderId(UUID.randomUUID())
            .customerId(UUID.randomUUID())
            .amount(BigDecimal.valueOf(100.00))
            .currency("USD")
            .paymentMethod(PaymentMethod.CREDIT_CARD)
            .idempotencyKey(UUID.randomUUID().toString())
            .build();
        
        assertThat(payment.getReferenceNumber()).isNull();
        
        payment.setReferenceNumber("TXN-12345");
        assertThat(payment.getReferenceNumber()).isEqualTo("TXN-12345");
    }
    
    @Test
    void shouldHandleVersionForOptimisticLocking() {
        Payment payment = new Payment.Builder()
            .orderId(UUID.randomUUID())
            .customerId(UUID.randomUUID())
            .amount(BigDecimal.valueOf(100.00))
            .currency("USD")
            .paymentMethod(PaymentMethod.CREDIT_CARD)
            .idempotencyKey(UUID.randomUUID().toString())
            .build();
        
        assertThat(payment.getVersion()).isEqualTo(0);
        payment.setVersion(1);
        assertThat(payment.getVersion()).isEqualTo(1);
    }
}
