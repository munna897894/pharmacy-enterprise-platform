package com.jagapathi.pharmacy.audit.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagapathi.pharmacy.audit.domain.AuditAction;
import com.jagapathi.pharmacy.audit.domain.AuditResourceType;
import com.jagapathi.pharmacy.audit.domain.AuditStatus;
import com.jagapathi.pharmacy.audit.infrastructure.persistence.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditEventProcessorTest {

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    private AuditEventProcessor processor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        processor = new AuditEventProcessor(auditLogService, processedEventRepository, objectMapper);
    }

    @Test
    void processesFlatOrderCreatedEventEmittedByOrderService() {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String message = """
            {
              "eventId": "%s",
              "orderId": "%s",
              "customerId": "%s",
              "currency": "USD",
              "total": 24.99,
              "items": []
            }
            """.formatted(eventId, orderId, UUID.randomUUID());

        processor.process(message, AuditResourceType.ORDER);

        verify(auditLogService).createAuditLog(
            eq(orderId), eq("order-service"), eq(AuditAction.CREATE), eq(AuditResourceType.ORDER),
            isNull(), isNull(), eq(AuditStatus.SUCCESS), contains("\"orderId\""), isNull()
        );
        verify(processedEventRepository).save(argThat(event -> eventId.toString().equals(event.getEventId())));
    }

    @Test
    void processesFlatInventoryAndPaymentEvents() {
        UUID orderId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        String inventoryEvent = """
            {"eventId":"%s","eventType":"InventoryReserved","orderId":"%s","reservationId":"%s"}
            """.formatted(UUID.randomUUID(), orderId, UUID.randomUUID());
        String paymentEvent = """
            {"eventId":"%s","eventType":"PaymentCompleted","paymentId":"%s","orderId":"%s","amount":12.50}
            """.formatted(UUID.randomUUID(), paymentId, orderId);

        processor.process(inventoryEvent, AuditResourceType.INVENTORY);
        processor.process(paymentEvent, AuditResourceType.PAYMENT);

        verify(auditLogService).createAuditLog(
            eq(orderId), eq("inventory-service"), eq(AuditAction.CREATE), eq(AuditResourceType.INVENTORY),
            isNull(), isNull(), eq(AuditStatus.SUCCESS), anyString(), isNull()
        );
        verify(auditLogService).createAuditLog(
            eq(paymentId), eq("payment-service"), eq(AuditAction.CREATE), eq(AuditResourceType.PAYMENT),
            isNull(), isNull(), eq(AuditStatus.SUCCESS), anyString(), isNull()
        );
    }

    @Test
    void processesDocumentedEnvelopeAndUsesItsPayload() {
        UUID aggregateId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        String message = """
            {
              "eventId": "%s",
              "eventType": "OrderConfirmed",
              "eventVersion": 1,
              "aggregateType": "Order",
              "aggregateId": "%s",
              "actorId": "%s",
              "payload": {"status":"CONFIRMED"}
            }
            """.formatted(eventId, aggregateId, actorId);

        processor.process(message, AuditResourceType.ORDER);

        ArgumentCaptor<String> details = ArgumentCaptor.forClass(String.class);
        verify(auditLogService).createAuditLog(
            eq(aggregateId), eq("order-service"), eq(AuditAction.UPDATE), eq(AuditResourceType.ORDER),
            eq(actorId), isNull(), eq(AuditStatus.SUCCESS), details.capture(), isNull()
        );
        assertThat(details.getValue()).isEqualTo("{\"status\":\"CONFIRMED\"}");
    }

    @Test
    void duplicateEventDoesNotCreateAnotherAuditLog() {
        String eventId = UUID.randomUUID().toString();
        String message = """
            {"eventId":"%s","eventType":"PaymentFailed","paymentId":"%s","orderId":"%s"}
            """.formatted(eventId, UUID.randomUUID(), UUID.randomUUID());
        when(processedEventRepository.existsById(eventId)).thenReturn(true);

        processor.process(message, AuditResourceType.PAYMENT);

        verify(auditLogService, never()).createAuditLog(
            any(), anyString(), any(), any(), any(), any(), any(), any(), any()
        );
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void rejectsMalformedEventsSoKafkaCanRetryOrRouteToDlt() {
        assertThatThrownBy(() -> processor.process("{not-json", AuditResourceType.ORDER))
            .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(auditLogService, processedEventRepository);
    }
}
