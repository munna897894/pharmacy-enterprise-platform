package com.jagapathi.pharmacy.audit.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jagapathi.pharmacy.audit.domain.AuditAction;
import com.jagapathi.pharmacy.audit.domain.AuditResourceType;
import com.jagapathi.pharmacy.audit.domain.AuditStatus;
import com.jagapathi.pharmacy.audit.domain.ProcessedEvent;
import com.jagapathi.pharmacy.audit.infrastructure.persistence.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class AuditEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(AuditEventProcessor.class);

    private static final Map<AuditResourceType, String> SERVICE_NAMES = Map.of(
        AuditResourceType.PRODUCT, "product-service",
        AuditResourceType.CUSTOMER, "customer-service",
        AuditResourceType.INVENTORY, "inventory-service",
        AuditResourceType.ORDER, "order-service",
        AuditResourceType.PAYMENT, "payment-service",
        AuditResourceType.NOTIFICATION, "notification-service",
        AuditResourceType.PRESCRIPTION, "prescription-service",
        AuditResourceType.PHARMACY, "pharmacy-service"
    );

    private final AuditLogService auditLogService;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    public AuditEventProcessor(AuditLogService auditLogService,
                               ProcessedEventRepository processedEventRepository,
                               ObjectMapper objectMapper) {
        this.auditLogService = auditLogService;
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void process(String message, AuditResourceType resourceType) {
        JsonNode event = readEvent(message);
        String eventType = text(event, "eventType");
        if (eventType == null) {
            eventType = inferLegacyEventType(event, resourceType);
        }

        AuditAction action = actionFor(eventType, resourceType);
        if (action == null) {
            return;
        }

        String eventId = requiredText(event, "eventId");
        if (processedEventRepository.existsById(eventId)) {
            return;
        }

        UUID aggregateId = readAggregateId(event, resourceType);
        UUID actorId = readActorId(event);
        String details = writeDetails(event);
        AuditStatus status = isFailure(eventType) ? AuditStatus.FAILURE : AuditStatus.SUCCESS;

        auditLogService.createAuditLog(
            aggregateId,
            SERVICE_NAMES.get(resourceType),
            action,
            resourceType,
            actorId,
            null,
            status,
            details,
            null
        );
        processedEventRepository.save(new ProcessedEvent(eventId));
        var lifecycleLog = log.atLevel(status == AuditStatus.FAILURE
            ? org.slf4j.event.Level.WARN : org.slf4j.event.Level.INFO);
        lifecycleLog
            .addKeyValue("eventName", "audit.event.recorded")
            .addKeyValue("eventId", eventId)
            .addKeyValue("eventType", eventType)
            .addKeyValue("aggregateId", aggregateId)
            .addKeyValue("resourceType", resourceType)
            .addKeyValue("auditStatus", status)
            .addKeyValue("outcome", status == AuditStatus.FAILURE ? "business_failure_recorded" : "success");
        String orderId = text(event, "orderId");
        if (orderId != null) {
            lifecycleLog.addKeyValue("orderId", orderId);
        }
        lifecycleLog.log("Audit lifecycle event");
    }

    private JsonNode readEvent(String message) {
        try {
            JsonNode event = objectMapper.readTree(message);
            if (event == null || !event.isObject()) {
                throw new IllegalArgumentException("Kafka event must be a JSON object");
            }
            return event;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Kafka event is not valid JSON", exception);
        }
    }

    private String inferLegacyEventType(JsonNode event, AuditResourceType resourceType) {
        return switch (resourceType) {
            case ORDER -> event.has("orderId") ? "OrderCreated" : null;
            case INVENTORY -> null;
            default -> null;
        };
    }

    private AuditAction actionFor(String eventType, AuditResourceType resourceType) {
        return switch (resourceType) {
            case PRODUCT -> switch (eventType) {
                case "ProductCreated" -> AuditAction.CREATE;
                case "ProductUpdated" -> AuditAction.UPDATE;
                case "ProductDeleted" -> AuditAction.DELETE;
                default -> null;
            };
            case CUSTOMER -> switch (eventType) {
                case "CustomerRegistered" -> AuditAction.CREATE;
                case "CustomerUpdated", "CustomerAddressChanged" -> AuditAction.UPDATE;
                default -> null;
            };
            case INVENTORY -> switch (eventType) {
                case "StockLevelCreated", "InventoryReserved" -> AuditAction.CREATE;
                case "StockAdjusted", "InventoryRejected", "InventoryReleased" -> AuditAction.UPDATE;
                default -> null;
            };
            case ORDER -> switch (eventType) {
                case "OrderCreated" -> AuditAction.CREATE;
                case "OrderCancelled" -> AuditAction.DELETE;
                case "OrderFulfilled", "OrderConfirmed", "OrderReadyForPickup" -> AuditAction.UPDATE;
                default -> null;
            };
            case PAYMENT -> switch (eventType) {
                case "PaymentProcessed", "PaymentCompleted", "PaymentFailed" -> AuditAction.CREATE;
                case "PaymentRefunded", "PaymentRefundedEvent" -> AuditAction.UPDATE;
                default -> null;
            };
            case NOTIFICATION -> switch (eventType) {
                case "NotificationSent", "NotificationFailed" -> AuditAction.CREATE;
                default -> null;
            };
            case PRESCRIPTION -> switch (eventType) {
                case "PrescriptionCreated" -> AuditAction.CREATE;
                case "PrescriptionActivated", "PrescriptionFilled", "PrescriptionVerified",
                    "PrescriptionRejected" -> AuditAction.UPDATE;
                default -> null;
            };
            case PHARMACY -> switch (eventType) {
                case "PharmacyCreated" -> AuditAction.CREATE;
                case "PharmacyUpdated", "PharmacyStatusChanged" -> AuditAction.UPDATE;
                default -> null;
            };
            default -> null;
        };
    }

    private UUID readAggregateId(JsonNode event, AuditResourceType resourceType) {
        String aggregateId = text(event, "aggregateId");
        if (aggregateId == null) {
            String resourceIdField = switch (resourceType) {
                case INVENTORY, ORDER -> "orderId";
                case PAYMENT -> "paymentId";
                case NOTIFICATION -> "notificationId";
                case PRESCRIPTION -> "prescriptionId";
                case CUSTOMER -> "customerId";
                case PRODUCT -> "productId";
                case PHARMACY -> "pharmacyId";
                default -> null;
            };
            aggregateId = resourceIdField == null ? null : text(event, resourceIdField);
        }
        if (aggregateId == null) {
            throw new IllegalArgumentException("Kafka event is missing its aggregate identifier");
        }
        try {
            return UUID.fromString(aggregateId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Kafka event has an invalid aggregate identifier", exception);
        }
    }

    private UUID readActorId(JsonNode event) {
        String actorId = text(event, "actorId");
        if (actorId == null || actorId.isBlank() || "system".equalsIgnoreCase(actorId)) {
            return null;
        }
        try {
            return UUID.fromString(actorId);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Kafka event has an invalid actor identifier", exception);
        }
    }

    private String writeDetails(JsonNode event) {
        JsonNode details = event.get("payload");
        if (details == null || details.isNull()) {
            ObjectNode legacyPayload = ((ObjectNode) event).deepCopy();
            for (String metadataField : new String[] {
                "eventId", "eventType", "eventVersion", "aggregateType", "aggregateId",
                "occurredAt", "producer", "correlationId", "causationId", "actorId"
            }) {
                legacyPayload.remove(metadataField);
            }
            details = legacyPayload;
        }
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize audit event details", exception);
        }
    }

    private String requiredText(JsonNode event, String field) {
        String value = text(event, field);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Kafka event is missing required field " + field);
        }
        return value;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isValueNode() ? null : value.asText();
    }

    private boolean isFailure(String eventType) {
        return "PaymentFailed".equals(eventType)
            || "InventoryRejected".equals(eventType)
            || "PrescriptionRejected".equals(eventType)
            || "NotificationFailed".equals(eventType);
    }
}
