package com.jagapathi.pharmacy.audit.infrastructure.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagapathi.pharmacy.audit.application.AuditLogService;
import com.jagapathi.pharmacy.audit.domain.AuditAction;
import com.jagapathi.pharmacy.audit.domain.AuditResourceType;
import com.jagapathi.pharmacy.audit.domain.AuditStatus;
import com.jagapathi.pharmacy.audit.infrastructure.persistence.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@org.junit.jupiter.api.Disabled("Kafka consumer logic now in KafkaConsumerConfig - tested via integration tests")
class ProductEventConsumerTest {
    @Mock
    private AuditLogService auditLogService;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ProductEventConsumer consumer;

    @Test
    void testProductCreatedEventProcessing() throws Exception {
        String eventId = UUID.randomUUID().toString();
        UUID aggregateId = UUID.randomUUID();
        String eventJson = """
            {
                "eventId": "%s",
                "eventType": "ProductCreated",
                "aggregateId": "%s",
                "actorId": "%s",
                "payload": {"sku": "PROD001", "name": "Test Product"}
            }
            """.formatted(eventId, aggregateId, UUID.randomUUID());

        Message<String> message = MessageBuilder.withPayload(eventJson).build();

        when(processedEventRepository.findByEventId(eventId)).thenReturn(Optional.empty());
        when(objectMapper.readTree(eventJson)).thenReturn(
            new com.fasterxml.jackson.databind.ObjectMapper().readTree(eventJson)
        );

        var eventConsumer = consumer.productEventConsumer();
        eventConsumer.accept(message);

        verify(auditLogService).createAuditLog(any(), anyString(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void testDuplicateProductEventHandling() throws Exception {
        String eventId = UUID.randomUUID().toString();
        UUID aggregateId = UUID.randomUUID();
        String eventJson = """
            {
                "eventId": "%s",
                "eventType": "ProductCreated",
                "aggregateId": "%s",
                "actorId": "%s",
                "payload": {"sku": "PROD001", "name": "Test Product"}
            }
            """.formatted(eventId, aggregateId, UUID.randomUUID());

        Message<String> message = MessageBuilder.withPayload(eventJson).build();

        when(processedEventRepository.existsById(eventId)).thenReturn(true);

        var eventConsumer = consumer.productEventConsumer();
        eventConsumer.accept(message);

        verify(auditLogService, never()).createAuditLog(any(), anyString(), any(), any(), any(), any(), any(), any(), any());
    }
}
