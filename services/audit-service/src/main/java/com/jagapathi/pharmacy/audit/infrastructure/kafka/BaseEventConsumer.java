package com.jagapathi.pharmacy.audit.infrastructure.kafka;

import com.jagapathi.pharmacy.audit.application.AuditLogService;
import com.jagapathi.pharmacy.audit.domain.AuditAction;
import com.jagapathi.pharmacy.audit.domain.AuditResourceType;
import com.jagapathi.pharmacy.audit.domain.AuditStatus;
import com.jagapathi.pharmacy.audit.infrastructure.persistence.ProcessedEventRepository;
import com.jagapathi.pharmacy.audit.domain.ProcessedEvent;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class BaseEventConsumer {
    protected final AuditLogService auditLogService;
    protected final ProcessedEventRepository processedEventRepository;

    public BaseEventConsumer(AuditLogService auditLogService, ProcessedEventRepository processedEventRepository) {
        this.auditLogService = auditLogService;
        this.processedEventRepository = processedEventRepository;
    }

    protected boolean isEventProcessed(String eventId) {
        return processedEventRepository.findByEventId(eventId).isPresent();
    }

    protected void markEventAsProcessed(String eventId) {
        if (!isEventProcessed(eventId)) {
            processedEventRepository.save(new ProcessedEvent(eventId));
        }
    }

    protected void recordAuditLog(UUID aggregateId, String service, AuditAction action,
                                  AuditResourceType resourceType, UUID userId, String username,
                                  AuditStatus status, String details, String eventId) {
        if (!isEventProcessed(eventId)) {
            auditLogService.createAuditLog(aggregateId, service, action, resourceType,
                userId, username, status, details, null);
            markEventAsProcessed(eventId);
        }
    }
}
