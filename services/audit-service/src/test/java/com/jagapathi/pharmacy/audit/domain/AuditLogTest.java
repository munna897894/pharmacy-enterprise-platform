package com.jagapathi.pharmacy.audit.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditLogTest {
    @Test
    void testAuditLogCreation() {
        UUID aggregateId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant timestamp = Instant.now();
        String details = "{\"key\":\"value\"}";

        AuditLog auditLog = new AuditLog(
            aggregateId, "product-service", AuditAction.CREATE, AuditResourceType.PRODUCT,
            userId, "testuser", timestamp, AuditStatus.SUCCESS, details, "192.168.1.1"
        );

        assertThat(auditLog.getAggregateId()).isEqualTo(aggregateId);
        assertThat(auditLog.getService()).isEqualTo("product-service");
        assertThat(auditLog.getAction()).isEqualTo(AuditAction.CREATE);
        assertThat(auditLog.getResourceType()).isEqualTo(AuditResourceType.PRODUCT);
        assertThat(auditLog.getUserId()).isEqualTo(userId);
        assertThat(auditLog.getUsername()).isEqualTo("testuser");
        assertThat(auditLog.getStatus()).isEqualTo(AuditStatus.SUCCESS);
        assertThat(auditLog.getDetails()).isEqualTo(details);
        assertThat(auditLog.getIpAddress()).isEqualTo("192.168.1.1");
        assertThat(auditLog.getCreatedAt()).isNotNull();
    }

    @Test
    void testAuditLogWithoutOptionalFields() {
        UUID aggregateId = UUID.randomUUID();
        Instant timestamp = Instant.now();

        AuditLog auditLog = new AuditLog(
            aggregateId, "auth-service", AuditAction.LOGIN, AuditResourceType.USER,
            null, null, timestamp, AuditStatus.SUCCESS, null, null
        );

        assertThat(auditLog.getAggregateId()).isEqualTo(aggregateId);
        assertThat(auditLog.getService()).isEqualTo("auth-service");
        assertThat(auditLog.getAction()).isEqualTo(AuditAction.LOGIN);
        assertThat(auditLog.getUserId()).isNull();
        assertThat(auditLog.getUsername()).isNull();
        assertThat(auditLog.getDetails()).isNull();
        assertThat(auditLog.getIpAddress()).isNull();
    }

    @Test
    void testAuditStatusTransitions() {
        UUID aggregateId = UUID.randomUUID();
        AuditLog auditLog = new AuditLog(
            aggregateId, "test-service", AuditAction.UPDATE, AuditResourceType.ORDER,
            null, null, Instant.now(), AuditStatus.FAILURE, null, null
        );

        assertThat(auditLog.getStatus()).isEqualTo(AuditStatus.FAILURE);

        auditLog.setStatus(AuditStatus.SUCCESS);
        assertThat(auditLog.getStatus()).isEqualTo(AuditStatus.SUCCESS);
    }

    @Test
    void testProcessedEventCreation() {
        String eventId = "event-123-abc";
        ProcessedEvent processedEvent = new ProcessedEvent(eventId);

        assertThat(processedEvent.getEventId()).isEqualTo(eventId);
        assertThat(processedEvent.getProcessedAt()).isNotNull();
    }
}
