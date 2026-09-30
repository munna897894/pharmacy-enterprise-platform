package com.jagapathi.pharmacy.notification.infrastructure.messaging;

import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration
public class NotificationKafkaProducerConfig {

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(KafkaProperties properties) {
        ProducerFactory<String, String> producerFactory =
            new DefaultKafkaProducerFactory<>(properties.buildProducerProperties(null));
        KafkaTemplate<String, String> template = new KafkaTemplate<>(producerFactory);
        template.setObservationEnabled(true);
        return template;
    }
}
