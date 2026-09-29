package com.jagapathi.pharmacy.audit.application;

import com.jagapathi.pharmacy.audit.api.AuditLogResponse;
import com.jagapathi.pharmacy.audit.api.ComplianceReportResponse;
import com.jagapathi.pharmacy.audit.domain.AuditAction;
import com.jagapathi.pharmacy.audit.domain.AuditLog;
import com.jagapathi.pharmacy.audit.domain.AuditResourceType;
import com.jagapathi.pharmacy.audit.domain.AuditStatus;
import com.jagapathi.pharmacy.audit.infrastructure.persistence.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {
    @Mock
    private AuditLogRepository repository;

    @InjectMocks
    private AuditLogService service;

    private UUID aggregateId;
    private UUID userId;
    private AuditLog auditLog;

    @BeforeEach
    void setUp() {
        aggregateId = UUID.randomUUID();
        userId = UUID.randomUUID();
        auditLog = new AuditLog(
            aggregateId, "product-service", AuditAction.CREATE, AuditResourceType.PRODUCT,
            userId, "testuser", Instant.now(), AuditStatus.SUCCESS, "{}", "192.168.1.1"
        );
    }

    @Test
    void testCreateAuditLog() {
        when(repository.save(any(AuditLog.class))).thenReturn(auditLog);

        AuditLogResponse response = service.createAuditLog(
            aggregateId, "product-service", AuditAction.CREATE, AuditResourceType.PRODUCT,
            userId, "testuser", AuditStatus.SUCCESS, "{}", "192.168.1.1"
        );

        assertThat(response).isNotNull();
        assertThat(response.aggregateId()).isEqualTo(aggregateId);
        assertThat(response.service()).isEqualTo("product-service");
        assertThat(response.action()).isEqualTo(AuditAction.CREATE);
        assertThat(response.resourceType()).isEqualTo(AuditResourceType.PRODUCT);
        verify(repository).save(any(AuditLog.class));
    }

    @Test
    void testGetAuditTrail() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<AuditLog> auditLogs = new PageImpl<>(Arrays.asList(auditLog));
        when(repository.findByAggregateIdOrderByTimestampDesc(aggregateId, pageable))
            .thenReturn(auditLogs);

        Page<AuditLogResponse> result = service.getAuditTrail(aggregateId, pageable);

        assertThat(result).isNotEmpty();
        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(repository).findByAggregateIdOrderByTimestampDesc(aggregateId, pageable);
    }

    @Test
    void testGetUserActivityHistory() {
        Instant startDate = Instant.now().minus(java.time.Duration.ofDays(30));
        Instant endDate = Instant.now();
        Pageable pageable = PageRequest.of(0, 10);
        Page<AuditLog> auditLogs = new PageImpl<>(Arrays.asList(auditLog));

        when(repository.findUserActivityHistory(userId, startDate, endDate, pageable))
            .thenReturn(auditLogs);

        Page<AuditLogResponse> result = service.getUserActivityHistory(userId, startDate, endDate, pageable);

        assertThat(result).isNotEmpty();
        assertThat(result.getTotalElements()).isEqualTo(1);
        verify(repository).findUserActivityHistory(userId, startDate, endDate, pageable);
    }

    @Test
    void testGetComplianceReport() {
        Instant startDate = Instant.now().minus(java.time.Duration.ofDays(90));
        Instant endDate = Instant.now();
        List<AuditLog> auditLogs = Arrays.asList(auditLog);

        when(repository.findComplianceReport(AuditResourceType.PRODUCT, startDate, endDate))
            .thenReturn(auditLogs);

        ComplianceReportResponse report = service.getComplianceReport(
            AuditResourceType.PRODUCT, startDate, endDate
        );

        assertThat(report).isNotNull();
        assertThat(report.resourceType()).isEqualTo(AuditResourceType.PRODUCT);
        assertThat(report.totalActions()).isEqualTo(1);
        assertThat(report.failureCount()).isEqualTo(0);
        assertThat(report.actionCounts()).containsKey(AuditAction.CREATE);
    }

    @Test
    void testSearchAuditLogs() {
        Instant startDate = Instant.now().minus(java.time.Duration.ofDays(30));
        Instant endDate = Instant.now();
        Pageable pageable = PageRequest.of(0, 10);
        Page<AuditLog> auditLogs = new PageImpl<>(Arrays.asList(auditLog));

        when(repository.searchAuditLogs(
            eq(AuditResourceType.PRODUCT), eq(AuditAction.CREATE),
            eq(userId), eq("product-service"), eq(startDate), eq(endDate), eq(pageable)
        )).thenReturn(auditLogs);

        Page<AuditLogResponse> result = service.searchAuditLogs(
            AuditResourceType.PRODUCT, AuditAction.CREATE, userId, "product-service",
            startDate, endDate, pageable
        );

        assertThat(result).isNotEmpty();
        verify(repository).searchAuditLogs(
            eq(AuditResourceType.PRODUCT), eq(AuditAction.CREATE),
            eq(userId), eq("product-service"), eq(startDate), eq(endDate), eq(pageable)
        );
    }

    @Test
    void testComplianceReportWithMultipleActions() {
        AuditLog log1 = new AuditLog(
            aggregateId, "product-service", AuditAction.CREATE, AuditResourceType.PRODUCT,
            userId, "user1", Instant.now(), AuditStatus.SUCCESS, "{}", "192.168.1.1"
        );
        AuditLog log2 = new AuditLog(
            aggregateId, "product-service", AuditAction.UPDATE, AuditResourceType.PRODUCT,
            userId, "user2", Instant.now(), AuditStatus.SUCCESS, "{}", "192.168.1.1"
        );
        AuditLog log3 = new AuditLog(
            aggregateId, "product-service", AuditAction.UPDATE, AuditResourceType.PRODUCT,
            userId, "user2", Instant.now(), AuditStatus.FAILURE, "{}", "192.168.1.1"
        );

        Instant startDate = Instant.now().minus(java.time.Duration.ofDays(90));
        Instant endDate = Instant.now();
        List<AuditLog> auditLogs = Arrays.asList(log1, log2, log3);

        when(repository.findComplianceReport(AuditResourceType.PRODUCT, startDate, endDate))
            .thenReturn(auditLogs);

        ComplianceReportResponse report = service.getComplianceReport(
            AuditResourceType.PRODUCT, startDate, endDate
        );

        assertThat(report.totalActions()).isEqualTo(3);
        assertThat(report.failureCount()).isEqualTo(1);
        assertThat(report.actionCounts()).containsEntry(AuditAction.CREATE, 1L);
        assertThat(report.actionCounts()).containsEntry(AuditAction.UPDATE, 2L);
        assertThat(report.affectedUsers()).containsExactlyInAnyOrder("user1", "user2");
    }
}
