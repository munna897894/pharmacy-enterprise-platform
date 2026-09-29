package com.jagapathi.pharmacy.notification.api;

import com.jagapathi.pharmacy.notification.domain.Channel;
import com.jagapathi.pharmacy.notification.domain.NotificationType;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SendNotificationRequest(
    @NotNull(message = "customerId is required")
    String customerId,
    
    @NotNull(message = "type is required")
    NotificationType type,
    
    @NotEmpty(message = "channels cannot be empty")
    List<Channel> channels
) {
}
