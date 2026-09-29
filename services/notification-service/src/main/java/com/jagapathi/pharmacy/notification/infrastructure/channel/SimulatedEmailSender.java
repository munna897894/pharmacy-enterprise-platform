package com.jagapathi.pharmacy.notification.infrastructure.channel;

import com.jagapathi.pharmacy.notification.domain.Channel;
import com.jagapathi.pharmacy.notification.domain.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default email channel: records delivery without an SMTP server. Only the notification id is
 * logged so recipient addresses never reach the logs.
 */
@Component
@ConditionalOnProperty(name = "notification.email.mode", havingValue = "simulated", matchIfMissing = true)
public class SimulatedEmailSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(SimulatedEmailSender.class);

    @Override
    public void send(Notification notification) {
        if (!supports(notification.getChannel())) {
            throw new IllegalArgumentException("SimulatedEmailSender does not support channel: "
                + notification.getChannel());
        }
        log.info("Simulated email delivered notificationId={} type={}", notification.getId(), notification.getType());
    }

    @Override
    public boolean supports(Channel channel) {
        return channel == Channel.EMAIL;
    }
}
