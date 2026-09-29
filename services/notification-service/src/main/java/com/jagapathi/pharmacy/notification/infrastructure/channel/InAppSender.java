package com.jagapathi.pharmacy.notification.infrastructure.channel;

import com.jagapathi.pharmacy.notification.domain.Channel;
import com.jagapathi.pharmacy.notification.domain.Notification;
import com.jagapathi.pharmacy.notification.infrastructure.persistence.NotificationRepository;
import org.springframework.stereotype.Component;

@Component
public class InAppSender implements NotificationSender {
    
    private final NotificationRepository notificationRepository;

    public InAppSender(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Override
    public void send(Notification notification) throws Exception {
        if (!supports(notification.getChannel())) {
            throw new IllegalArgumentException("InAppSender does not support channel: " + notification.getChannel());
        }

        notification.markAsSent();
        notificationRepository.save(notification);
    }

    @Override
    public boolean supports(Channel channel) {
        return channel == Channel.IN_APP;
    }
}
