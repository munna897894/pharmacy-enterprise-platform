package com.jagapathi.pharmacy.notification.api;

import com.jagapathi.pharmacy.notification.domain.Channel;
import com.jagapathi.pharmacy.notification.domain.NotificationStatus;
import com.jagapathi.pharmacy.notification.domain.NotificationType;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    UUID customerId,
    NotificationType type,
    Channel channel,
    String recipient,
    String subject,
    String message,
    NotificationStatus status,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    Instant sentAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    Instant readAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    Instant createdAt,
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'", timezone = "UTC")
    Instant updatedAt
) {
}
