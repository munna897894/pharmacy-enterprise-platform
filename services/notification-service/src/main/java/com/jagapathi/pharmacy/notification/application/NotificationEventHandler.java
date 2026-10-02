package com.jagapathi.pharmacy.notification.application;

import com.jagapathi.pharmacy.notification.domain.Channel;
import com.jagapathi.pharmacy.notification.domain.NotificationType;
import com.jagapathi.pharmacy.notification.domain.OrderCustomer;
import com.jagapathi.pharmacy.notification.domain.ProcessedEvent;
import com.jagapathi.pharmacy.notification.infrastructure.persistence.OrderCustomerRepository;
import com.jagapathi.pharmacy.notification.infrastructure.persistence.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Idempotent reactions to domain events. Each method records the eventId in the inbox table
 * within the same transaction as its side effects, so redeliveries are no-ops.
 */
@Service
public class NotificationEventHandler {

    private static final String SIMULATED_EMAIL = "customer@example.com";
    private static final Logger log = LoggerFactory.getLogger(NotificationEventHandler.class);

    private final NotificationService notificationService;
    private final OrderCustomerRepository orderCustomerRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final Clock clock;

    public NotificationEventHandler(NotificationService notificationService,
                                    OrderCustomerRepository orderCustomerRepository,
                                    ProcessedEventRepository processedEventRepository,
                                    Clock clock) {
        this.notificationService = notificationService;
        this.orderCustomerRepository = orderCustomerRepository;
        this.processedEventRepository = processedEventRepository;
        this.clock = clock;
    }

    @Transactional
    public void onOrderCreated(String eventId, UUID orderId, UUID customerId) {
        if (alreadyProcessed(eventId)) {
            return;
        }
        if (!orderCustomerRepository.existsById(orderId)) {
            orderCustomerRepository.save(new OrderCustomer(orderId, customerId, clock.instant()));
        }
        markProcessed(eventId);
        logEventProcessed("notification.order_projection.updated", eventId, orderId, "success");
    }

    @Transactional
    public void onPaymentCompleted(String eventId, UUID orderId, BigDecimal amount, String currency) {
        if (alreadyProcessed(eventId)) {
            return;
        }
        notificationService.sendNotification(customerFor(orderId), orderId, NotificationType.ORDER_CONFIRMATION,
            List.of(Channel.EMAIL, Channel.IN_APP),
            Map.of("orderId", orderId.toString(),
                "amount", plain(amount),
                "currency", nullToEmpty(currency),
                "email", SIMULATED_EMAIL));
        markProcessed(eventId);
        logEventProcessed("notification.payment_completed.handled", eventId, orderId, "success");
    }

    @Transactional
    public void onPaymentFailed(String eventId, UUID orderId, String failureCode) {
        if (alreadyProcessed(eventId)) {
            return;
        }
        notificationService.sendNotification(customerFor(orderId), orderId, NotificationType.PAYMENT_FAILED,
            List.of(Channel.EMAIL, Channel.IN_APP),
            Map.of("orderId", orderId.toString(),
                "failureCode", failureCode == null ? "UNKNOWN" : failureCode,
                "email", SIMULATED_EMAIL));
        markProcessed(eventId);
        logEventProcessed("notification.payment_failed.handled", eventId, orderId, "failure_notified");
    }

    @Transactional
    public void onPaymentRefunded(String eventId, UUID orderId, BigDecimal refundAmount) {
        if (alreadyProcessed(eventId)) {
            return;
        }
        notificationService.sendNotification(customerFor(orderId), orderId, NotificationType.REFUND_PROCESSED,
            List.of(Channel.EMAIL),
            Map.of("orderId", orderId.toString(), "amount", plain(refundAmount), "email", SIMULATED_EMAIL));
        markProcessed(eventId);
        logEventProcessed("notification.payment_refunded.handled", eventId, orderId, "success");
    }

    @Transactional
    public void onPrescriptionVerified(String eventId, UUID prescriptionId, UUID customerId) {
        if (alreadyProcessed(eventId)) {
            return;
        }
        notificationService.sendNotification(customerId, null, NotificationType.PRESCRIPTION_VERIFIED,
            List.of(Channel.IN_APP), Map.of("prescriptionId", prescriptionId.toString()));
        markProcessed(eventId);
    }

    @Transactional
    public void onPrescriptionRejected(String eventId, UUID prescriptionId, UUID customerId, String reasonCode) {
        if (alreadyProcessed(eventId)) {
            return;
        }
        notificationService.sendNotification(customerId, null, NotificationType.PRESCRIPTION_REJECTED,
            List.of(Channel.IN_APP),
            Map.of("prescriptionId", prescriptionId.toString(),
                "reasonCode", reasonCode == null ? "UNSPECIFIED" : reasonCode));
        markProcessed(eventId);
    }

    private UUID customerFor(UUID orderId) {
        return orderCustomerRepository.findById(orderId)
            .map(OrderCustomer::getCustomerId)
            .orElseThrow(() -> new OrderCustomerNotYetKnownException(orderId));
    }

    private boolean alreadyProcessed(String eventId) {
        return processedEventRepository.existsById(eventId);
    }

    private void markProcessed(String eventId) {
        processedEventRepository.save(new ProcessedEvent(eventId));
    }

    private void logEventProcessed(String eventName, String eventId, UUID orderId, String outcome) {
        log.atInfo()
            .addKeyValue("eventName", eventName)
            .addKeyValue("eventId", eventId)
            .addKeyValue("orderId", orderId)
            .addKeyValue("outcome", outcome)
            .log("Notification event lifecycle event");
    }

    private static String plain(BigDecimal value) {
        return value == null ? "" : value.toPlainString();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
