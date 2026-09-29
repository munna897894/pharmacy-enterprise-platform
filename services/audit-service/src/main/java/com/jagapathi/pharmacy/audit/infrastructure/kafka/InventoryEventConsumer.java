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

public class InventoryEventConsumer extends BaseEventConsumer {
    private static final Logger logger = Logger.getLogger(InventoryEventConsumer.class.getName());
    private final ObjectMapper objectMapper;

    public InventoryEventConsumer(AuditLogService auditLogService,
                                  ProcessedEventRepository processedEventRepository,
                                  ObjectMapper objectMapper) {
        super(auditLogService, processedEventRepository);
        this.objectMapper = objectMapper;
    }

    public Consumer<Message<String>> inventoryEventConsumer() {
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
                    case "StockLevelCreated" -> AuditAction.CREATE;
                    case "StockAdjusted" -> AuditAction.UPDATE;
                    default -> null;
                };

                if (action != null) {
                    String details = event.get("payload").toString();
                    auditLogService.createAuditLog(
                        aggregateId,
                        "inventory-service",
                        action,
                        AuditResourceType.INVENTORY,
                        actorId != null ? UUID.fromString(actorId) : null,
                        null,
                        AuditStatus.SUCCESS,
                        details,
                        null
                    );
                    markEventAsProcessed(eventId);
                }
            } catch (Exception e) {
                logger.severe("Error processing inventory event: " + e.getMessage());
            }
        };
    }
}
