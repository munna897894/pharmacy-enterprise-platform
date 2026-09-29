package com.jagapathi.pharmacy.order.infrastructure;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {

    @Id
    @Column(columnDefinition = "CHAR(36)")
    private UUID id;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, columnDefinition = "CHAR(36)")
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, length = 50)
    private String topic;

    @Column(name = "event_key", columnDefinition = "CHAR(36)")
    private String eventKey;

    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String payload;

    @Column(columnDefinition = "LONGTEXT")
    private String headers;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(nullable = false)
    private Boolean published = false;

    @Column(nullable = false)
    private Integer attempts = 0;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(nullable = false)
    private Instant createdAt;

    public OutboxEvent() {
    }

    public OutboxEvent(UUID id, String aggregateType, UUID aggregateId, String eventType, 
                      String topic, String eventKey, String payload, String headers) {
        this.id = id;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.topic = topic;
        this.eventKey = eventKey;
        this.payload = payload;
        this.headers = headers;
        this.occurredAt = Instant.now();
        this.createdAt = Instant.now();
        this.published = false;
        this.attempts = 0;
    }

    public void recordPublishSuccess() {
        this.published = true;
        this.publishedAt = Instant.now();
    }

    public void recordPublishFailure(String errorMessage) {
        this.attempts++;
        this.lastError = errorMessage;
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getTopic() {
        return topic;
    }

    public String getEventKey() {
        return eventKey;
    }

    public String getPayload() {
        return payload;
    }

    public String getHeaders() {
        return headers;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Boolean getPublished() {
        return published;
    }

    public Integer getAttempts() {
        return attempts;
    }

    public String getLastError() {
        return lastError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
