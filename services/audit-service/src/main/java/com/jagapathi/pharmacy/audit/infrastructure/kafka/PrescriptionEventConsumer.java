package com.jagapathi.pharmacy.audit.infrastructure.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagapathi.pharmacy.audit.application.AuditLogService;
import com.jagapathi.pharmacy.audit.domain.AuditAction;
import com.jagapathi.pharmacy.audit.domain.AuditResourceType;
import com.jagapathi.pharmacy.audit.domain.AuditStatus;
import com.jagapathi.pharmacy.audit.infrastructure.persistence.ProcessedEventRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

public class PrescriptionEventConsumer extends BaseEventConsumer {
    private static final Logger logger = Logger.getLogger(PrescriptionEventConsumer.class.getName());
    private final ObjectMapper objectMapper;

    public PrescriptionEventConsumer(AuditLogService auditLogService,
                                     ProcessedEventRepository processedEventRepository,
                                     ObjectMapper objectMapper) {
        super(auditLogService, processedEventRepository);
        this.objectMapper = objectMapper;
    }

    public Consumer<Message<String>> prescriptionEventConsumer() {
        return message -> {
            try {
                String payload = message.getPayload();
                JsonNode event = objectMapper.readTree(payload);

                String eventType = event.get("eventType").asText();
                String eventId = event.get("eventId").asText();
                UUID aggregateId = UUID.fromString(event.get("aggregateId").asText());
                String actorId = event.has("actorId") ? event.get("actorId").asText() : null;

                if (isEventProcessed(eventId)) {
                    return;
                }

                AuditAction action = switch (eventType) {
                    case "PrescriptionCreated" -> AuditAction.CREATE;
                    case "PrescriptionActivated" -> AuditAction.UPDATE;
                    case "PrescriptionFilled" -> AuditAction.UPDATE;
                    default -> null;
                };

                if (action != null) {
                    String details = event.get("payload").toString();
                    auditLogService.createAuditLog(
                        aggregateId,
                        "prescription-service",
                        action,
                        AuditResourceType.PRESCRIPTION,
                        actorId != null ? UUID.fromString(actorId) : null,
                        null,
                        AuditStatus.SUCCESS,
                        details,
                        null
                    );
                    markEventAsProcessed(eventId);
                }
            } catch (Exception e) {
                logger.severe("Error processing prescription event: " + e.getMessage());
            }
        };
    }
}
