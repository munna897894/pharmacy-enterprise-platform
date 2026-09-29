package com.jagapathi.pharmacy.audit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "audit_logs",
    indexes = {
        @Index(name = "idx_aggregate_id_timestamp", columnList = "aggregate_id,timestamp"),
        @Index(name = "idx_user_id_timestamp", columnList = "user_id,timestamp"),
        @Index(name = "idx_resource_type_timestamp_action", columnList = "resource_type,timestamp,action")
    }
)
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "VARCHAR(36)")
    private UUID id;

    @Column(name = "aggregate_id", nullable = false, columnDefinition = "VARCHAR(36)")
    private UUID aggregateId;

    @Column(name = "service", nullable = false, length = 50)
    private String service;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 30)
    private AuditResourceType resourceType;

    @Column(name = "user_id", columnDefinition = "VARCHAR(36)")
    private UUID userId;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "timestamp", nullable = false, columnDefinition = "TIMESTAMP(6)")
    private Instant timestamp;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AuditStatus status;

    @Lob
    @Column(name = "details", columnDefinition = "JSON")
    private String details;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
    private Instant createdAt;

    public AuditLog() {
    }

    public AuditLog(UUID aggregateId, String service, AuditAction action, AuditResourceType resourceType,
                    UUID userId, String username, Instant timestamp, AuditStatus status,
                    String details, String ipAddress) {
        this.aggregateId = aggregateId;
        this.service = service;
        this.action = action;
        this.resourceType = resourceType;
        this.userId = userId;
        this.username = username;
        this.timestamp = timestamp;
        this.status = status;
        this.details = details;
        this.ipAddress = ipAddress;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public void setAggregateId(UUID aggregateId) {
        this.aggregateId = aggregateId;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public AuditAction getAction() {
        return action;
    }

    public void setAction(AuditAction action) {
        this.action = action;
    }

    public AuditResourceType getResourceType() {
        return resourceType;
    }

    public void setResourceType(AuditResourceType resourceType) {
        this.resourceType = resourceType;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public AuditStatus getStatus() {
        return status;
    }

    public void setStatus(AuditStatus status) {
        this.status = status;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
