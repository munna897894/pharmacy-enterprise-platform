package com.jagapathi.pharmacy.audit.api;

import com.jagapathi.pharmacy.audit.application.AuditLogService;
import com.jagapathi.pharmacy.audit.domain.AuditAction;
import com.jagapathi.pharmacy.audit.domain.AuditResourceType;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {
    private final AuditLogService auditLogService;

    public AuditController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping("/{aggregateId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AuditLogResponse>> getAuditTrail(
        @PathVariable @NotNull UUID aggregateId,
        Pageable pageable) {
        Page<AuditLogResponse> result = auditLogService.getAuditTrail(aggregateId, pageable);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AuditLogResponse>> searchAuditLogs(
        @RequestParam(required = false) AuditResourceType resourceType,
        @RequestParam(required = false) AuditAction action,
        @RequestParam(required = false) UUID userId,
        @RequestParam(required = false) String service,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
        Pageable pageable) {

        Instant actualStartDate = startDate != null ? startDate : Instant.now().minus(java.time.Duration.ofDays(90));
        Instant actualEndDate = endDate != null ? endDate : Instant.now();

        Page<AuditLogResponse> result = auditLogService.searchAuditLogs(
            resourceType, action, userId, service, actualStartDate, actualEndDate, pageable
        );
        return ResponseEntity.ok(result);
    }

    @GetMapping("/compliance/report")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ComplianceReportResponse> getComplianceReport(
        @RequestParam @NotNull AuditResourceType resourceType,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate) {

        ComplianceReportResponse result = auditLogService.getComplianceReport(
            resourceType, startDate, endDate
        );
        return ResponseEntity.ok(result);
    }
}

