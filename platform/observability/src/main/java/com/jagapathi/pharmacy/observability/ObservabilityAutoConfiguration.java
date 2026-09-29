package com.jagapathi.pharmacy.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.http.client.ClientHttpRequestInterceptor;

@AutoConfiguration
public class ObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnMissingBean(CorrelationIdFilter.class)
    FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter() {
        FilterRegistrationBean<CorrelationIdFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new CorrelationIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean(MeterRegistryCustomizer.class)
    MeterRegistryCustomizer<MeterRegistry> pharmacyCommonMetricTags(Environment environment) {
        String service = environment.getProperty("spring.application.name", "unknown-service");
        String deploymentEnvironment = environment.getProperty("ENVIRONMENT", "local");
        return registry -> registry.config().commonTags(
            "service", service,
            "environment", deploymentEnvironment
        );
    }

    @Bean
    @ConditionalOnMissingBean
    PharmacyBusinessMetrics pharmacyBusinessMetrics(MeterRegistry meterRegistry) {
        return new PharmacyBusinessMetrics(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean
    BusinessMetricsAspect businessMetricsAspect(PharmacyBusinessMetrics metrics) {
        return new BusinessMetricsAspect(metrics);
    }

    @Bean
    @ConditionalOnMissingBean
    OutboxMetricsRecorder outboxMetricsRecorder(MeterRegistry meterRegistry) {
        return new OutboxMetricsRecorder(meterRegistry);
    }

    @Bean
    @ConditionalOnClass(org.springframework.web.client.RestTemplate.class)
    RestTemplateCustomizer pharmacyCorrelationIdRestTemplateCustomizer() {
        ClientHttpRequestInterceptor interceptor = (request, body, execution) -> {
            String correlationId = org.slf4j.MDC.get(CorrelationIdFilter.MDC_KEY);
            if (correlationId != null && !correlationId.isBlank()
                && !request.getHeaders().containsKey(CorrelationIdFilter.HEADER_NAME)) {
                request.getHeaders().set(CorrelationIdFilter.HEADER_NAME, correlationId);
            }
            return execution.execute(request, body);
        };
        return restTemplate -> restTemplate.getInterceptors().add(interceptor);
    }
}
