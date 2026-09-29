package com.jagapathi.pharmacy.notification.infrastructure.channel;

import com.jagapathi.pharmacy.notification.domain.Channel;
import com.jagapathi.pharmacy.notification.domain.Notification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;

@Component
public class SmsSender implements NotificationSender {
    
    private final RestTemplate restTemplate;
    private final String externalServiceUrl;

    public SmsSender(RestTemplate restTemplate, @Value("${external-service.url:http://external-mock-service:8080}") String externalServiceUrl) {
        this.restTemplate = restTemplate;
        this.externalServiceUrl = externalServiceUrl;
    }

    @Override
    public void send(Notification notification) throws Exception {
        if (!supports(notification.getChannel())) {
            throw new IllegalArgumentException("SmsSender does not support channel: " + notification.getChannel());
        }

        Map<String, Object> body = new HashMap<>();
        body.put("phoneNumber", notification.getRecipient());
        body.put("message", notification.getMessage());

        restTemplate.postForObject(externalServiceUrl + "/api/v1/send-sms", body, String.class);
    }

    @Override
    public boolean supports(Channel channel) {
        return channel == Channel.SMS;
    }
}
