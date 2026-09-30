package com.jagapathi.pharmacy.payment.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagapathi.pharmacy.observability.BusinessMetric;
import com.jagapathi.pharmacy.observability.OutboxMetricsSnapshot;
import com.jagapathi.pharmacy.observability.RecordBusinessMetric;
import com.jagapathi.pharmacy.observability.TraceContextHeaders;
import com.jagapathi.pharmacy.payment.api.PaymentProcessedEvent;
import com.jagapathi.pharmacy.payment.api.PaymentRefundedEvent;
import com.jagapathi.pharmacy.payment.api.PaymentResponse;
import com.jagapathi.pharmacy.payment.domain.*;
import com.jagapathi.pharmacy.payment.infrastructure.OutboxRepository;
import com.jagapathi.pharmacy.payment.infrastructure.OrderLookupClient;
import com.jagapathi.pharmacy.payment.infrastructure.PaymentGatewayClient;
import com.jagapathi.pharmacy.payment.infrastructure.PaymentRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@Transactional
public class PaymentService {

    private static final long KAFKA_SEND_TIMEOUT_MS = 10_000L;
    
    private final PaymentRepository paymentRepository;
    private final OutboxRepository outboxRepository;
    private final PaymentGatewayClient gatewayClient;
    private final OrderLookupClient orderLookupClient;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    public PaymentService(PaymentRepository paymentRepository,
                         OutboxRepository outboxRepository,
                         PaymentGatewayClient gatewayClient,
                         OrderLookupClient orderLookupClient,
                         KafkaTemplate<String, String> kafkaTemplate,
                         ObjectMapper objectMapper) {
        this.paymentRepository = paymentRepository;
        this.outboxRepository = outboxRepository;
        this.gatewayClient = gatewayClient;
        this.orderLookupClient = orderLookupClient;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Process payment with idempotency support.
     * If the idempotency key exists, return the existing payment.
     * Otherwise, create a new payment and call the external gateway.
     */
    @RecordBusinessMetric(BusinessMetric.PAYMENT)
    public PaymentResponse processPayment(UUID orderId, UUID customerId, BigDecimal amount, 
                                         String currency, PaymentMethod paymentMethod, 
                                         String idempotencyKey) {
        // Check for duplicate by idempotency key
        var existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existingPayment.isPresent()) {
            Payment existing = existingPayment.get();
            if (!existing.getOrderId().equals(orderId) || !existing.getCustomerId().equals(customerId)) {
                throw new DuplicatePaymentException("Idempotency key is already used for another payment");
            }
            return mapToResponse(existing);
        }
        
        // Create new payment in PENDING state
        var orderSummary = orderLookupClient.getOrderSummary(orderId);
        if (orderSummary != null && !orderSummary.customerId().equals(customerId)) {
            throw new IllegalArgumentException("Customer does not match the order");
        }
        if (orderSummary != null && orderSummary.total() != null && orderSummary.total().compareTo(amount) != 0) {
            throw new IllegalArgumentException("Payment amount does not match order total");
        }

        Payment payment = new Payment.Builder()
            .orderId(orderId)
            .customerId(customerId)
            .amount(amount)
            .currency(currency)
            .paymentMethod(paymentMethod)
            .status(PaymentStatus.PENDING)
            .idempotencyKey(idempotencyKey)
            .build();
        
        payment = paymentRepository.save(payment);
        
        try {
            // Attempt to process with gateway
            payment.setStatus(PaymentStatus.PROCESSING);
            payment = paymentRepository.save(payment);
            
            String reference = "PAY-" + payment.getId();
            PaymentGatewayClient.GatewayResponse gatewayResponse = 
                gatewayClient.processPayment(amount, currency, paymentMethod, reference);
            
            if (gatewayResponse.isSuccess()) {
                payment.setStatus(PaymentStatus.SUCCESS);
                payment.setReferenceNumber(gatewayResponse.getTransactionId());
                publishPaymentEvent(payment, PaymentStatus.SUCCESS, gatewayResponse.getTransactionId(), null);
            } else {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setReferenceNumber(gatewayResponse.getMessage());
                publishPaymentEvent(payment, PaymentStatus.FAILED, null, "GATEWAY_DECLINED");
            }
        } catch (Exception e) {
            payment.setStatus(PaymentStatus.FAILED);
            publishPaymentEvent(payment, PaymentStatus.FAILED, null, "GATEWAY_ERROR");
        }
        
        payment.setUpdatedAt(Instant.now());
        payment = paymentRepository.save(payment);
        
        return mapToResponse(payment);
    }
    
    /**
     * Refund a payment. Only SUCCESS payments can be refunded.
     */
    @RecordBusinessMetric(BusinessMetric.PAYMENT)
    public PaymentResponse refundPayment(UUID paymentId, String reason) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));
        
        if (payment.getStatus() == PaymentStatus.FAILED || payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new InvalidPaymentStateException(
                "Cannot refund payment with status: " + payment.getStatus());
        }
        
        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setUpdatedAt(Instant.now());
        payment = paymentRepository.save(payment);
        
        publishRefundEvent(payment);
        
        return mapToResponse(payment);
    }
    
    /**
     * Get payment status by payment ID.
     */
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentStatus(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new PaymentNotFoundException("Payment not found: " + paymentId));
        return mapToResponse(payment);
    }
    
    /**
     * Get payment by order ID.
     */
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByOrderId(UUID orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new PaymentNotFoundException("Payment not found for order: " + orderId));
        return mapToResponse(payment);
    }
    
    /**
     * Publish outbox events to Kafka.
     * This should be called periodically by a scheduled task.
     */
    @Transactional
    public OutboxMetricsSnapshot publishOutboxEvents() {
        var unpublishedEvents = outboxRepository.findUnpublished();
        long publishedCount = 0;
        long failedCount = 0;
        for (OutboxEvent event : unpublishedEvents) {
            try {
                var record = new org.apache.kafka.clients.producer.ProducerRecord<String, String>(
                    "pharmacy.payment.events.v1", event.getAggregateId().toString(), event.getPayload());
                var capturedHeaders = readOutboxHeaders(event.getHeaders());
                capturedHeaders.forEach((name, value) ->
                    record.headers().add(name, value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                TraceContextHeaders.callInCapturedContext(capturedHeaders, () -> kafkaTemplate.send(record))
                    .get(KAFKA_SEND_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
                event.setPublished(true);
                outboxRepository.save(event);
                publishedCount++;
            } catch (Exception e) {
                failedCount++;
            }
        }
        var pendingEvents = unpublishedEvents.stream().filter(event -> !event.isPublished()).toList();
        long oldestAgeSeconds = pendingEvents.stream()
            .map(OutboxEvent::getCreatedAt)
            .min(Instant::compareTo)
            .map(createdAt -> Math.max(0, Duration.between(createdAt, Instant.now()).getSeconds()))
            .orElse(0L);
        return new OutboxMetricsSnapshot(pendingEvents.size(), oldestAgeSeconds, publishedCount, failedCount);
    }
    
    private void publishPaymentEvent(Payment payment, PaymentStatus status,
                                     String providerReference, String failureCode) {
        try {
            UUID eventId = UUID.randomUUID();
            boolean success = status == PaymentStatus.SUCCESS;
            Instant now = Instant.now();

            PaymentProcessedEvent event = new PaymentProcessedEvent(
                eventId,
                success ? "PaymentCompleted" : "PaymentFailed",
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getCurrency(),
                providerReference,
                failureCode,
                success ? now : null,
                success ? null : now
            );
            
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outboxEvent = new OutboxEvent(
                eventId,
                event.eventType(),
                payment.getId(),
                payload,
                objectMapper.writeValueAsString(TraceContextHeaders.capture())
            );
            
            outboxRepository.save(outboxEvent);
        } catch (Exception e) {
            // Log error but don't throw - payment is already persisted
        }
    }
    
    private void publishRefundEvent(Payment payment) {
        try {
            UUID eventId = UUID.randomUUID();
            
            PaymentRefundedEvent event = new PaymentRefundedEvent(
                eventId,
                "PaymentRefundedEvent",
                "Payment",
                payment.getId(),
                Instant.now(),
                "payment-service",
                UUID.randomUUID(),
                payment.getId(),
                payment.getOrderId(),
                payment.getAmount()
            );
            
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outboxEvent = new OutboxEvent(
                eventId,
                "PaymentRefundedEvent",
                payment.getId(),
                payload,
                objectMapper.writeValueAsString(TraceContextHeaders.capture())
            );
            
            outboxRepository.save(outboxEvent);
        } catch (Exception e) {
            // Log error but don't throw
        }
    }
    
    private java.util.Map<String, String> readOutboxHeaders(String serializedHeaders) throws Exception {
        var allowed = new java.util.LinkedHashMap<String, String>();
        if (serializedHeaders == null || serializedHeaders.isBlank()) {
            return allowed;
        }
        var fields = objectMapper.readTree(serializedHeaders).fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            if (TraceContextHeaders.isAllowedHeader(entry.getKey()) && entry.getValue().isTextual()) {
                allowed.put(entry.getKey(), entry.getValue().asText());
            }
        }
        return allowed;
    }

    private PaymentResponse mapToResponse(Payment payment) {
        return new PaymentResponse(
            payment.getId(),
            payment.getOrderId(),
            payment.getCustomerId(),
            payment.getAmount(),
            payment.getCurrency(),
            payment.getPaymentMethod().name(),
            payment.getStatus().name(),
            payment.getReferenceNumber(),
            payment.getCreatedAt(),
            payment.getUpdatedAt()
        );
    }
}
