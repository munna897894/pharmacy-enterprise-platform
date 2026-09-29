package com.jagapathi.pharmacy.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_templates")
public class NotificationTemplate {
    
    @Id
    private UUID id;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private NotificationType type;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private Channel channel;
    
    @Column(name = "subject_template", length = 255)
    private String subjectTemplate;
    
    @Column(name = "message_template", nullable = false, columnDefinition = "TEXT")
    private String messageTemplate;
    
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public NotificationTemplate() {
    }

    public NotificationTemplate(UUID id, NotificationType type, Channel channel,
                               String subjectTemplate, String messageTemplate) {
        this.id = id;
        this.type = type;
        this.channel = channel;
        this.subjectTemplate = subjectTemplate;
        this.messageTemplate = messageTemplate;
        this.isActive = true;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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

    public String getSubjectTemplate() {
        return subjectTemplate;
    }

    public void setSubjectTemplate(String subjectTemplate) {
        this.subjectTemplate = subjectTemplate;
    }

    public String getMessageTemplate() {
        return messageTemplate;
    }

    public void setMessageTemplate(String messageTemplate) {
        this.messageTemplate = messageTemplate;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
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

    public String renderSubject(java.util.Map<String, String> variables) {
        if (subjectTemplate == null) {
            return null;
        }
        return renderTemplate(subjectTemplate, variables);
    }

    public String renderMessage(java.util.Map<String, String> variables) {
        return renderTemplate(messageTemplate, variables);
    }

    private String renderTemplate(String template, java.util.Map<String, String> variables) {
        if (template == null || variables == null) {
            return template;
        }
        String result = template;
        for (java.util.Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue() != null ? entry.getValue() : "");
        }
        return result;
    }
}
