package com.jagapathi.pharmacy.notification.infrastructure.channel;

import com.jagapathi.pharmacy.notification.domain.Notification;
import org.springframework.stereotype.Component;

@Component
public interface NotificationSender {
    void send(Notification notification) throws Exception;
    boolean supports(com.jagapathi.pharmacy.notification.domain.Channel channel);
}
