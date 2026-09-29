package com.jagapathi.pharmacy.audit.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.jagapathi.pharmacy.audit.domain.AuditAction;
import com.jagapathi.pharmacy.audit.domain.AuditResourceType;
import com.jagapathi.pharmacy.audit.domain.AuditStatus;
import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
    @JsonProperty("id")
    UUID id,

    @JsonProperty("aggregateId")
    UUID aggregateId,

    @JsonProperty("service")
    String service,

    @JsonProperty("action")
    AuditAction action,

    @JsonProperty("resourceType")
    AuditResourceType resourceType,

    @JsonProperty("userId")
    UUID userId,

    @JsonProperty("username")
    String username,

    @JsonProperty("timestamp")
    Instant timestamp,

    @JsonProperty("status")
    AuditStatus status,

    @JsonProperty("details")
    String details,

    @JsonProperty("ipAddress")
    String ipAddress
) {
}
