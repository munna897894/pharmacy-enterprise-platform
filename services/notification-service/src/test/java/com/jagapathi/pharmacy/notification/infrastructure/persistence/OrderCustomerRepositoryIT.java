package com.jagapathi.pharmacy.notification.infrastructure.persistence;

import com.jagapathi.pharmacy.notification.domain.Channel;
import com.jagapathi.pharmacy.notification.domain.NotificationType;
import com.jagapathi.pharmacy.notification.domain.OrderCustomer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class OrderCustomerRepositoryIT {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.3");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void configureDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", MYSQL::getDriverClassName);
        registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.MySQLDialect");
    }

    @Autowired
    private OrderCustomerRepository orderCustomerRepository;

    @Autowired
    private NotificationTemplateRepository templateRepository;

    @Test
    void persistsAndResolvesOrderCustomerProjection() {
        UUID orderId = UUID.fromString("feb836ca-e0d4-454b-88eb-74f231b90470");
        UUID customerId = UUID.fromString("0f93fc97-c263-493e-bcd7-a07e7f9d2119");
        Instant recordedAt = Instant.parse("2026-09-29T12:00:00Z");

        orderCustomerRepository.saveAndFlush(new OrderCustomer(orderId, customerId, recordedAt));

        assertThat(orderCustomerRepository.existsById(orderId)).isTrue();
        assertThat(orderCustomerRepository.findById(orderId)).hasValueSatisfying(projection -> {
            assertThat(projection.getCustomerId()).isEqualTo(customerId);
            assertThat(projection.getRecordedAt().truncatedTo(ChronoUnit.SECONDS)).isEqualTo(recordedAt);
        });
    }

    @Test
    void migrationSeedsTemplatesForEventDrivenNotifications() {
        assertThat(templateRepository.findByTypeAndChannelAndIsActiveTrue(
            NotificationType.ORDER_CONFIRMATION, Channel.EMAIL)).isPresent();
        assertThat(templateRepository.findByTypeAndChannelAndIsActiveTrue(
            NotificationType.ORDER_CONFIRMATION, Channel.IN_APP)).isPresent();
        assertThat(templateRepository.findByTypeAndChannelAndIsActiveTrue(
            NotificationType.PAYMENT_FAILED, Channel.EMAIL)).isPresent();
        assertThat(templateRepository.findByTypeAndChannelAndIsActiveTrue(
            NotificationType.PRESCRIPTION_VERIFIED, Channel.IN_APP)).isPresent();
    }
}
