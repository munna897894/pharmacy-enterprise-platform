package com.jagapathi.pharmacy.audit.api;

import com.jagapathi.pharmacy.audit.application.AuditLogService;
import com.jagapathi.pharmacy.audit.domain.AuditAction;
import com.jagapathi.pharmacy.audit.domain.AuditResourceType;
import com.jagapathi.pharmacy.audit.domain.AuditStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditControllerTest {
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private AuditController controller;

    @Test
    void testGetAuditTrail() {
        UUID aggregateId = UUID.randomUUID();
        AuditLogResponse auditLog = new AuditLogResponse(
            UUID.randomUUID(), aggregateId, "product-service", AuditAction.CREATE,
            AuditResourceType.PRODUCT, null, "testuser", Instant.now(), AuditStatus.SUCCESS, "{}", "192.168.1.1"
        );
        Page<AuditLogResponse> page = new PageImpl<>(Arrays.asList(auditLog));

        when(auditLogService.getAuditTrail(eq(aggregateId), any()))
            .thenReturn(page);

        var result = controller.getAuditTrail(aggregateId, PageRequest.of(0, 10));

        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().getContent()).hasSize(1);
    }

    @Test
    void testSearchAuditLogs() {
        AuditLogResponse auditLog = new AuditLogResponse(
            UUID.randomUUID(), UUID.randomUUID(), "product-service", AuditAction.CREATE,
            AuditResourceType.PRODUCT, null, "testuser", Instant.now(), AuditStatus.SUCCESS, "{}", "192.168.1.1"
        );
        Page<AuditLogResponse> page = new PageImpl<>(Arrays.asList(auditLog));

        when(auditLogService.searchAuditLogs(
            eq(AuditResourceType.PRODUCT), eq(AuditAction.CREATE), any(), any(), any(), any(), any()
        )).thenReturn(page);

        var result = controller.searchAuditLogs(
            AuditResourceType.PRODUCT, AuditAction.CREATE, null, null,
            null, null, PageRequest.of(0, 10)
        );

        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(result.getBody().getContent()).hasSize(1);
    }

    @Test
    void testGetComplianceReport() {
        ComplianceReportResponse report = new ComplianceReportResponse(
            "2024-01-01 to 2024-06-30",
            AuditResourceType.PRODUCT,
            5,
            Map.of(AuditAction.CREATE, 2L, AuditAction.UPDATE, 3L),
            0,
            Set.of("user1", "user2"),
            Instant.now()
        );

        when(auditLogService.getComplianceReport(eq(AuditResourceType.PRODUCT), any(), any()))
            .thenReturn(report);

        Instant startDate = Instant.parse("2024-01-01T00:00:00Z");
        Instant endDate = Instant.parse("2024-06-30T23:59:59Z");

        var result = controller.getComplianceReport(AuditResourceType.PRODUCT, startDate, endDate);

        assertThat(result.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(result.getBody().resourceType()).isEqualTo(AuditResourceType.PRODUCT);
        assertThat(result.getBody().totalActions()).isEqualTo(5);
    }
}


