package com.jagapathi.pharmacy.audit.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jagapathi.pharmacy.audit.domain.AuditAction;
import com.jagapathi.pharmacy.audit.domain.AuditResourceType;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

public record ComplianceReportResponse(
    @JsonProperty("reportPeriod")
    String reportPeriod,

    @JsonProperty("resourceType")
    AuditResourceType resourceType,

    @JsonProperty("totalActions")
    long totalActions,

    @JsonProperty("actionCounts")
    Map<AuditAction, Long> actionCounts,

    @JsonProperty("failureCount")
    long failureCount,

    @JsonProperty("affectedUsers")
    Set<String> affectedUsers,

    @JsonProperty("generatedAt")
    Instant generatedAt
) {
}
