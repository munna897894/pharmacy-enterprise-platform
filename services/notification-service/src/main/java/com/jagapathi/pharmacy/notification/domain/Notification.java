package com.jagapathi.pharmacy.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {
    
    @Id
    private UUID id;
    
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private NotificationType type;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private Channel channel;
    
    @Column(name = "recipient", nullable = false, length = 255)
    private String recipient;
    
    @Column(name = "subject", length = 255)
    private String subject;
    
    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private NotificationStatus status;
    
    @Column(name = "sent_at")
    private Instant sentAt;
    
    @Column(name = "read_at")
    private Instant readAt;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "retry_count")
    private Integer retryCount = 0;

    public Notification() {
    }

    public Notification(UUID id, UUID customerId, NotificationType type, Channel channel,
                       String recipient, String subject, String message) {
        this.id = id;
        this.customerId = customerId;
        this.type = type;
        this.channel = channel;
        this.recipient = recipient;
        this.subject = subject;
        this.message = message;
        this.status = NotificationStatus.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.retryCount = 0;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public void setStatus(NotificationStatus status) {
        this.status = status;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public void markAsRead() {
        this.readAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markAsSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void markAsDelivered() {
        this.status = NotificationStatus.DELIVERED;
        this.updatedAt = Instant.now();
    }

    public void markAsFailed() {
        this.status = NotificationStatus.FAILED;
        this.updatedAt = Instant.now();
        this.retryCount = (this.retryCount != null ? this.retryCount : 0) + 1;
    }

    public boolean canRetry() {
        return this.status == NotificationStatus.FAILED && (this.retryCount == null || this.retryCount < 3);
    }
}
