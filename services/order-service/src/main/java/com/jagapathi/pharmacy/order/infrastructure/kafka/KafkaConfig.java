package com.jagapathi.pharmacy.order.infrastructure.kafka;

import com.jagapathi.pharmacy.observability.CorrelationIdRecordInterceptor;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;

@Configuration
@EnableKafka
public class KafkaConfig {

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(KafkaProperties properties) {
        var producerProps = properties.buildProducerProperties(null);
        ProducerFactory<String, String> producerFactory = new DefaultKafkaProducerFactory<>(producerProps);
        KafkaTemplate<String, String> template = new KafkaTemplate<>(producerFactory);
        template.setObservationEnabled(true);
        return template;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(KafkaProperties properties) {
        var consumerProps = properties.buildConsumerProperties(null);
        ConsumerFactory<String, String> consumerFactory = new DefaultKafkaConsumerFactory<>(consumerProps);
        ConcurrentKafkaListenerContainerFactory<String, String> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(new org.springframework.kafka.listener.DefaultErrorHandler());
        factory.setRecordInterceptor(new CorrelationIdRecordInterceptor());
        factory.getContainerProperties().setObservationEnabled(true);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setConcurrency(3);
        return factory;
    }
}
