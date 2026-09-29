package com.jagapathi.pharmacy.notification.infrastructure.channel;

import com.jagapathi.pharmacy.notification.domain.Channel;
import com.jagapathi.pharmacy.notification.domain.Notification;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "notification.email.mode", havingValue = "smtp")
public class EmailSender implements NotificationSender {
    
    private final JavaMailSender mailSender;

    public EmailSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public void send(Notification notification) throws Exception {
        if (!supports(notification.getChannel())) {
            throw new IllegalArgumentException("EmailSender does not support channel: " + notification.getChannel());
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(notification.getRecipient());
        message.setSubject(notification.getSubject());
        message.setText(notification.getMessage());
        message.setFrom("noreply@pharmacy.local");

        mailSender.send(message);
    }

    @Override
    public boolean supports(Channel channel) {
        return channel == Channel.EMAIL;
    }
}
