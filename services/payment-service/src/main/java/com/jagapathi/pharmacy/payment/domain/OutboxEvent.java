package com.jagapathi.pharmacy.payment.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox", indexes = {
    @Index(name = "idx_outbox_published", columnList = "published"),
    @Index(name = "idx_outbox_event_type", columnList = "event_type")
})
public class OutboxEvent {
    
    @Id
    @Column(columnDefinition = "CHAR(36)")
    private UUID id;
    
    @Column(name = "event_type", nullable = false)
    private String eventType;
    
    @Column(name = "aggregate_id", columnDefinition = "CHAR(36)", nullable = false)
    private UUID aggregateId;
    
    @Column(name = "payload", columnDefinition = "LONGTEXT", nullable = false)
    private String payload;

    @Column(name = "headers", columnDefinition = "LONGTEXT")
    private String headers;
    
    @Column(name = "published", nullable = false)
    private boolean published = false;
    
    @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMP(6)")
    private Instant createdAt;
    
    // Constructors
    public OutboxEvent() {}
    
    public OutboxEvent(UUID id, String eventType, UUID aggregateId, String payload) {
        this(id, eventType, aggregateId, payload, null);
    }

    public OutboxEvent(UUID id, String eventType, UUID aggregateId, String payload, String headers) {
        this.id = id;
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.payload = payload;
        this.headers = headers;
        this.published = false;
        this.createdAt = Instant.now();
    }
    
    // Getters and setters
    public UUID getId() {
        return id;
    }
    
    public void setId(UUID id) {
        this.id = id;
    }
    
    public String getEventType() {
        return eventType;
    }
    
    public void setEventType(String eventType) {
        this.eventType = eventType;
    }
    
    public UUID getAggregateId() {
        return aggregateId;
    }
    
    public void setAggregateId(UUID aggregateId) {
        this.aggregateId = aggregateId;
    }
    
    public String getPayload() {
        return payload;
    }
    
    public void setPayload(String payload) {
        this.payload = payload;
    }

    public String getHeaders() {
        return headers;
    }

    public void setHeaders(String headers) {
        this.headers = headers;
    }
    
    public boolean isPublished() {
        return published;
    }
    
    public void setPublished(boolean published) {
        this.published = published;
    }
    
    public Instant getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
