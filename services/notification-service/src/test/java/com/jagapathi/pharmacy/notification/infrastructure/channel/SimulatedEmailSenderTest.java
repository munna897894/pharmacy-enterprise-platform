package com.jagapathi.pharmacy.notification.infrastructure.channel;

import com.jagapathi.pharmacy.notification.domain.Channel;
import com.jagapathi.pharmacy.notification.domain.Notification;
import com.jagapathi.pharmacy.notification.domain.NotificationType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimulatedEmailSenderTest {

    private final SimulatedEmailSender sender = new SimulatedEmailSender();

    @Test
    void supportsOnlyEmail() {
        assertThat(sender.supports(Channel.EMAIL)).isTrue();
        assertThat(sender.supports(Channel.SMS)).isFalse();
        assertThat(sender.supports(Channel.IN_APP)).isFalse();
    }

    @Test
    void acceptsEmailNotificationsWithoutSmtp() {
        assertThatCode(() -> sender.send(notification(Channel.EMAIL))).doesNotThrowAnyException();
    }

    @Test
    void rejectsOtherChannels() {
        assertThatThrownBy(() -> sender.send(notification(Channel.SMS)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private static Notification notification(Channel channel) {
        return new Notification(UUID.fromString("6e0a7d1c-2b3a-4f5e-8d9c-0b1a2f3e4d10"),
            UUID.fromString("0f93fc97-c263-493e-bcd7-a07e7f9d2119"), NotificationType.ORDER_CONFIRMATION,
            channel, "customer@example.com", "subject", "message");
    }
}
