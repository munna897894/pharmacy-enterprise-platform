package com.jagapathi.pharmacy.payment.infrastructure;

import com.jagapathi.pharmacy.payment.domain.Payment;
import com.jagapathi.pharmacy.payment.domain.PaymentMethod;
import com.jagapathi.pharmacy.payment.domain.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class PaymentRepositoryIT extends AbstractMySqlRepositoryIT {
    
    @Autowired
    private TestEntityManager entityManager;
    
    @Autowired
    private PaymentRepository paymentRepository;
    
    @Test
    void shouldFindPaymentByOrderId() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        
        Payment payment = new Payment.Builder()
            .orderId(orderId)
            .customerId(customerId)
            .amount(BigDecimal.valueOf(100.00))
            .currency("USD")
            .paymentMethod(PaymentMethod.CREDIT_CARD)
            .idempotencyKey(idempotencyKey)
            .build();
        
        entityManager.persistAndFlush(payment);
        
        Optional<Payment> found = paymentRepository.findByOrderId(orderId);
        
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(payment.getId());
        assertThat(found.get().getOrderId()).isEqualTo(orderId);
    }
    
    @Test
    void shouldFindPaymentByIdempotencyKey() {
        String idempotencyKey = UUID.randomUUID().toString();
        
        Payment payment = new Payment.Builder()
            .orderId(UUID.randomUUID())
            .customerId(UUID.randomUUID())
            .amount(BigDecimal.valueOf(100.00))
            .currency("USD")
            .paymentMethod(PaymentMethod.CREDIT_CARD)
            .idempotencyKey(idempotencyKey)
            .build();
        
        entityManager.persistAndFlush(payment);
        
        Optional<Payment> found = paymentRepository.findByIdempotencyKey(idempotencyKey);
        
        assertThat(found).isPresent();
        assertThat(found.get().getIdempotencyKey()).isEqualTo(idempotencyKey);
    }
    
    @Test
    void shouldFindPaymentsByCustomerId() {
        UUID customerId = UUID.randomUUID();
        
        Payment payment1 = new Payment.Builder()
            .orderId(UUID.randomUUID())
            .customerId(customerId)
            .amount(BigDecimal.valueOf(100.00))
            .currency("USD")
            .paymentMethod(PaymentMethod.CREDIT_CARD)
            .idempotencyKey(UUID.randomUUID().toString())
            .build();
        
        Payment payment2 = new Payment.Builder()
            .orderId(UUID.randomUUID())
            .customerId(customerId)
            .amount(BigDecimal.valueOf(50.00))
            .currency("EUR")
            .paymentMethod(PaymentMethod.DEBIT_CARD)
            .idempotencyKey(UUID.randomUUID().toString())
            .build();
        
        entityManager.persistAndFlush(payment1);
        entityManager.persistAndFlush(payment2);
        
        List<Payment> payments = paymentRepository.findByCustomerId(customerId);
        
        assertThat(payments).hasSize(2);
    }
    
    @Test
    void shouldFindPaymentsByStatus() {
        PaymentStatus status = PaymentStatus.SUCCESS;
        
        Payment payment1 = new Payment.Builder()
            .orderId(UUID.randomUUID())
            .customerId(UUID.randomUUID())
            .amount(BigDecimal.valueOf(100.00))
            .currency("USD")
            .paymentMethod(PaymentMethod.CREDIT_CARD)
            .status(status)
            .idempotencyKey(UUID.randomUUID().toString())
            .build();
        
        Payment payment2 = new Payment.Builder()
            .orderId(UUID.randomUUID())
            .customerId(UUID.randomUUID())
            .amount(BigDecimal.valueOf(50.00))
            .currency("USD")
            .paymentMethod(PaymentMethod.CREDIT_CARD)
            .status(PaymentStatus.FAILED)
            .idempotencyKey(UUID.randomUUID().toString())
            .build();
        
        entityManager.persistAndFlush(payment1);
        entityManager.persistAndFlush(payment2);
        
        List<Payment> payments = paymentRepository.findByStatus(status);
        
        assertThat(payments).hasSize(1);
        assertThat(payments.get(0).getStatus()).isEqualTo(PaymentStatus.SUCCESS);
    }
    
    @Test
    void shouldEnforceIdempotencyKeyUniqueness() {
        String idempotencyKey = UUID.randomUUID().toString();
        
        Payment payment1 = new Payment.Builder()
            .orderId(UUID.randomUUID())
            .customerId(UUID.randomUUID())
            .amount(BigDecimal.valueOf(100.00))
            .currency("USD")
            .paymentMethod(PaymentMethod.CREDIT_CARD)
            .idempotencyKey(idempotencyKey)
            .build();
        
        Payment payment2 = new Payment.Builder()
            .orderId(UUID.randomUUID())
            .customerId(UUID.randomUUID())
            .amount(BigDecimal.valueOf(100.00))
            .currency("USD")
            .paymentMethod(PaymentMethod.CREDIT_CARD)
            .idempotencyKey(idempotencyKey)
            .build();
        
        entityManager.persistAndFlush(payment1);
        
        assertThatThrownBy(() -> {
            entityManager.persistAndFlush(payment2);
        }).isNotNull();
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
        
        entityManager.persistAndFlush(payment);
        
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setReferenceNumber("TXN-123");
        entityManager.persistAndFlush(payment);
        
        Payment updated = paymentRepository.findById(payment.getId()).orElse(null);
        
        assertThat(updated).isNotNull();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(updated.getReferenceNumber()).isEqualTo("TXN-123");
    }
}
