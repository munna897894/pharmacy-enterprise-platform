package com.jagapathi.pharmacy.audit.application;

import com.jagapathi.pharmacy.audit.api.AuditLogResponse;
import com.jagapathi.pharmacy.audit.api.ComplianceReportResponse;
import com.jagapathi.pharmacy.audit.domain.AuditAction;
import com.jagapathi.pharmacy.audit.domain.AuditLog;
import com.jagapathi.pharmacy.audit.domain.AuditResourceType;
import com.jagapathi.pharmacy.audit.domain.AuditStatus;
import com.jagapathi.pharmacy.audit.infrastructure.persistence.AuditLogRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class AuditLogService {
    private final AuditLogRepository repository;

    public AuditLogService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public AuditLogResponse createAuditLog(UUID aggregateId, String service, AuditAction action,
                                           AuditResourceType resourceType, UUID userId, String username,
                                           AuditStatus status, String details, String ipAddress) {
        AuditLog auditLog = new AuditLog(
            aggregateId, service, action, resourceType,
            userId, username, Instant.now(), status, details, ipAddress
        );
        AuditLog saved = repository.save(auditLog);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getAuditTrail(UUID aggregateId, Pageable pageable) {
        return repository.findByAggregateIdOrderByTimestampDesc(aggregateId, pageable)
            .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> getUserActivityHistory(UUID userId, Instant startDate, Instant endDate,
                                                         Pageable pageable) {
        return repository.findUserActivityHistory(userId, startDate, endDate, pageable)
            .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "compliance-reports", key = "#resourceType.toString() + '-' + #startDate.toString() + '-' + #endDate.toString()")
    public ComplianceReportResponse getComplianceReport(AuditResourceType resourceType, Instant startDate,
                                                        Instant endDate) {
        List<AuditLog> auditLogs = repository.findComplianceReport(resourceType, startDate, endDate);

        Map<AuditAction, Long> actionCounts = auditLogs.stream()
            .collect(Collectors.groupingBy(AuditLog::getAction, Collectors.counting()));

        long failureCount = auditLogs.stream()
            .filter(log -> log.getStatus() == AuditStatus.FAILURE)
            .count();

        Set<String> affectedUsers = auditLogs.stream()
            .map(AuditLog::getUsername)
            .filter(username -> username != null && !username.isBlank())
            .collect(Collectors.toSet());

        String reportPeriod = String.format("%s to %s", startDate, endDate);

        return new ComplianceReportResponse(
            reportPeriod,
            resourceType,
            auditLogs.size(),
            actionCounts,
            failureCount,
            affectedUsers,
            Instant.now()
        );
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> searchAuditLogs(AuditResourceType resourceType, AuditAction action,
                                                  UUID userId, String service,
                                                  Instant startDate, Instant endDate, Pageable pageable) {
        return repository.searchAuditLogs(resourceType, action, userId, service, startDate, endDate, pageable)
            .map(this::mapToResponse);
    }

    private AuditLogResponse mapToResponse(AuditLog auditLog) {
        return new AuditLogResponse(
            auditLog.getId(),
            auditLog.getAggregateId(),
            auditLog.getService(),
            auditLog.getAction(),
            auditLog.getResourceType(),
            auditLog.getUserId(),
            auditLog.getUsername(),
            auditLog.getTimestamp(),
            auditLog.getStatus(),
            auditLog.getDetails(),
            auditLog.getIpAddress()
        );
    }
}
