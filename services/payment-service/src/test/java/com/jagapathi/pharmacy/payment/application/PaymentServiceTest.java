package com.jagapathi.pharmacy.payment.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jagapathi.pharmacy.payment.api.PaymentResponse;
import com.jagapathi.pharmacy.payment.domain.*;
import com.jagapathi.pharmacy.payment.infrastructure.OutboxRepository;
import com.jagapathi.pharmacy.payment.infrastructure.OrderLookupClient;
import com.jagapathi.pharmacy.payment.infrastructure.PaymentGatewayClient;
import com.jagapathi.pharmacy.payment.infrastructure.PaymentRepository;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    
    @Mock
    private PaymentRepository paymentRepository;
    
    @Mock
    private OutboxRepository outboxRepository;
    
    @Mock
    private PaymentGatewayClient gatewayClient;
    @Mock
    private OrderLookupClient orderLookupClient;
    
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;
    
    private PaymentService paymentService;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        Mockito.lenient().when(orderLookupClient.getOrderSummary(any())).thenReturn(null);
        paymentService = new PaymentService(paymentRepository, outboxRepository, gatewayClient, orderLookupClient, kafkaTemplate, objectMapper);
    }
    
    @Test
    void shouldProcessPaymentSuccessfully() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        BigDecimal amount = BigDecimal.valueOf(100.00);
        
        PaymentGatewayClient.GatewayResponse gatewayResponse = 
            new PaymentGatewayClient.GatewayResponse(true, "TXN-123", "Success");
        
        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(gatewayClient.processPayment(eq(amount), eq("USD"), eq(PaymentMethod.CREDIT_CARD), any())).thenReturn(gatewayResponse);
        
        Payment savedPayment = new Payment.Builder()
            .orderId(orderId)
            .customerId(customerId)
            .amount(amount)
            .currency("USD")
            .paymentMethod(PaymentMethod.CREDIT_CARD)
            .status(PaymentStatus.SUCCESS)
            .idempotencyKey(idempotencyKey)
            .referenceNumber("TXN-123")
            .build();
        
        when(paymentRepository.save(any(Payment.class))).thenReturn(savedPayment);
        
        var logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(PaymentService.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        PaymentResponse response;
        try {
            response = paymentService.processPayment(orderId, customerId, amount, "USD",
                PaymentMethod.CREDIT_CARD, idempotencyKey);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
        
        assertThat(response).isNotNull();
        assertThat(response.orderId()).isEqualTo(orderId);
        assertThat(response.status()).isEqualTo("SUCCESS");
        assertThat(response.referenceNumber()).isEqualTo("TXN-123");
        assertThat(appender.list).anySatisfy(event -> {
            Map<String, Object> fields = event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
            assertThat(fields)
                .containsEntry("eventName", "payment.lifecycle.completed")
                .containsEntry("orderId", orderId)
                .containsEntry("status", PaymentStatus.SUCCESS)
                .containsKey("eventId")
                .doesNotContainKeys("amount", "customerId", "paymentMethod", "providerReference");
        });
        
        verify(paymentRepository, times(3)).save(any(Payment.class));
    }
    
    @Test
    void shouldReturnExistingPaymentForDuplicateIdempotencyKey() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        
        Payment existingPayment = new Payment.Builder()
            .orderId(orderId)
            .customerId(customerId)
            .amount(BigDecimal.valueOf(100.00))
            .currency("USD")
            .paymentMethod(PaymentMethod.CREDIT_CARD)
            .status(PaymentStatus.SUCCESS)
            .idempotencyKey(idempotencyKey)
            .build();
        
        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingPayment));
        
        PaymentResponse response = paymentService.processPayment(
            orderId, customerId, BigDecimal.valueOf(100.00), "USD", PaymentMethod.CREDIT_CARD, idempotencyKey
        );
        
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(existingPayment.getId());
        assertThat(response.status()).isEqualTo("SUCCESS");
        
        // Should not call gateway again
        verify(gatewayClient, never()).processPayment(any(), any(), any(), any());
    }
    
    @Test
    void shouldHandlePaymentGatewayFailure() {
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();
        BigDecimal amount = BigDecimal.valueOf(100.00);
        
        PaymentGatewayClient.GatewayResponse gatewayResponse = 
            new PaymentGatewayClient.GatewayResponse(false, null, "Card declined");
        
        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(gatewayClient.processPayment(eq(amount), eq("USD"), eq(PaymentMethod.CREDIT_CARD), any())).thenReturn(gatewayResponse);
        
        Payment failedPayment = new Payment.Builder()
            .orderId(orderId)
            .customerId(customerId)
            .amount(amount)
            .currency("USD")
            .paymentMethod(PaymentMethod.CREDIT_CARD)
            .status(PaymentStatus.FAILED)
            .idempotencyKey(idempotencyKey)
            .referenceNumber("Card declined")
            .build();
        
        when(paymentRepository.save(any(Payment.class))).thenReturn(failedPayment);
        
        var logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(PaymentService.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        PaymentResponse response;
        try {
            response = paymentService.processPayment(orderId, customerId, amount, "USD",
                PaymentMethod.CREDIT_CARD, idempotencyKey);
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
        
        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(appender.list).anySatisfy(event -> {
            Map<String, Object> fields = event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
            assertThat(fields)
                .containsEntry("eventName", "payment.lifecycle.failed")
                .containsEntry("orderId", orderId)
                .containsEntry("outcome", "failed")
                .containsEntry("failureCode", "GATEWAY_DECLINED")
                .doesNotContainKeys("amount", "customerId", "paymentMethod", "providerReference");
        });
    }
    
    @Test
    void shouldRefundSuccessfulPayment() {
        UUID paymentId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        
        Payment payment = new Payment.Builder()
            .id(paymentId)
            .orderId(orderId)
            .customerId(UUID.randomUUID())
            .amount(BigDecimal.valueOf(100.00))
            .currency("USD")
            .paymentMethod(PaymentMethod.CREDIT_CARD)
            .status(PaymentStatus.SUCCESS)
            .referenceNumber("TXN-123")
            .idempotencyKey(UUID.randomUUID().toString())
            .build();
        
        Payment refundedPayment = new Payment.Builder()
            .id(paymentId)
            .orderId(orderId)
            .customerId(payment.getCustomerId())
            .amount(payment.getAmount())
            .currency("USD")
            .paymentMethod(PaymentMethod.CREDIT_CARD)
            .status(PaymentStatus.REFUNDED)
            .idempotencyKey(payment.getIdempotencyKey())
            .build();
        
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenReturn(refundedPayment);
        
        PaymentResponse response = paymentService.refundPayment(paymentId, "Customer request");
        
        assertThat(response.status()).isEqualTo("REFUNDED");
        verify(paymentRepository).save(any(Payment.class));
    }
    
    @Test
    void shouldThrowExceptionWhenRefundingFailedPayment() {
        UUID paymentId = UUID.randomUUID();
        
        Payment failedPayment = new Payment.Builder()
            .id(paymentId)
            .orderId(UUID.randomUUID())
            .customerId(UUID.randomUUID())
            .amount(BigDecimal.valueOf(100.00))
            .currency("USD")
            .paymentMethod(PaymentMethod.CREDIT_CARD)
            .status(PaymentStatus.FAILED)
            .idempotencyKey(UUID.randomUUID().toString())
            .build();
        
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(failedPayment));
        
        assertThatThrownBy(() -> paymentService.refundPayment(paymentId, "Customer request"))
            .isInstanceOf(InvalidPaymentStateException.class)
            .hasMessageContaining("Cannot refund payment with status");
    }
    
    @Test
    void shouldThrowExceptionWhenRefundingAlreadyRefundedPayment() {
        UUID paymentId = UUID.randomUUID();
        
        Payment refundedPayment = new Payment.Builder()
            .id(paymentId)
            .orderId(UUID.randomUUID())
            .customerId(UUID.randomUUID())
            .amount(BigDecimal.valueOf(100.00))
            .currency("USD")
            .paymentMethod(PaymentMethod.CREDIT_CARD)
            .status(PaymentStatus.REFUNDED)
            .idempotencyKey(UUID.randomUUID().toString())
            .build();
        
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(refundedPayment));
        
        assertThatThrownBy(() -> paymentService.refundPayment(paymentId, "Customer request"))
            .isInstanceOf(InvalidPaymentStateException.class);
    }
    
    @Test
    void shouldGetPaymentStatus() {
        UUID paymentId = UUID.randomUUID();
        
        Payment payment = new Payment.Builder()
            .id(paymentId)
            .orderId(UUID.randomUUID())
            .customerId(UUID.randomUUID())
            .amount(BigDecimal.valueOf(100.00))
            .currency("USD")
            .paymentMethod(PaymentMethod.CREDIT_CARD)
            .status(PaymentStatus.SUCCESS)
            .referenceNumber("TXN-123")
            .idempotencyKey(UUID.randomUUID().toString())
            .build();
        
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        
        PaymentResponse response = paymentService.getPaymentStatus(paymentId);
        
        assertThat(response.id()).isEqualTo(paymentId);
        assertThat(response.status()).isEqualTo("SUCCESS");
    }
    
    @Test
    void shouldThrowExceptionWhenPaymentNotFound() {
        UUID paymentId = UUID.randomUUID();
        
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.empty());
        
        assertThatThrownBy(() -> paymentService.getPaymentStatus(paymentId))
            .isInstanceOf(PaymentNotFoundException.class);
    }
    
    @Test
    void shouldGetPaymentByOrderId() {
        UUID orderId = UUID.randomUUID();
        
        Payment payment = new Payment.Builder()
            .orderId(orderId)
            .customerId(UUID.randomUUID())
            .amount(BigDecimal.valueOf(100.00))
            .currency("USD")
            .paymentMethod(PaymentMethod.CREDIT_CARD)
            .idempotencyKey(UUID.randomUUID().toString())
            .build();
        
        when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));
        
        PaymentResponse response = paymentService.getPaymentByOrderId(orderId);
        
        assertThat(response.orderId()).isEqualTo(orderId);
    }
}
