package com.jagapathi.pharmacy.notification.application;

import com.jagapathi.pharmacy.notification.domain.Channel;
import com.jagapathi.pharmacy.notification.domain.NotificationType;
import com.jagapathi.pharmacy.notification.domain.OrderCustomer;
import com.jagapathi.pharmacy.notification.domain.ProcessedEvent;
import com.jagapathi.pharmacy.notification.infrastructure.persistence.OrderCustomerRepository;
import com.jagapathi.pharmacy.notification.infrastructure.persistence.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class NotificationEventHandlerTest {

    private static final Instant NOW = Instant.parse("2026-09-29T12:00:00Z");
    private static final String EVENT_ID = "3f0e5c55-5a8c-4b1f-9a55-3e2b8a0c1d01";
    private static final UUID ORDER_ID = UUID.fromString("0b9b6f0e-6d4c-4a3e-9c52-7f1f0a2b3c4d");
    private static final UUID CUSTOMER_ID = UUID.fromString("5d2c8e1a-9b7f-4c6d-8e3a-1f2b3c4d5e6f");
    private static final UUID PRESCRIPTION_ID = UUID.fromString("7a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c4d");

    private NotificationService notificationService;
    private OrderCustomerRepository orderCustomerRepository;
    private ProcessedEventRepository processedEventRepository;
    private NotificationEventHandler handler;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        orderCustomerRepository = mock(OrderCustomerRepository.class);
        processedEventRepository = mock(ProcessedEventRepository.class);
        handler = new NotificationEventHandler(notificationService, orderCustomerRepository,
            processedEventRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void orderCreatedRecordsProjectionAndInboxEntry() {
        handler.onOrderCreated(EVENT_ID, ORDER_ID, CUSTOMER_ID);

        ArgumentCaptor<OrderCustomer> captor = ArgumentCaptor.forClass(OrderCustomer.class);
        verify(orderCustomerRepository).save(captor.capture());
        assertThat(captor.getValue().getOrderId()).isEqualTo(ORDER_ID);
        assertThat(captor.getValue().getCustomerId()).isEqualTo(CUSTOMER_ID);
        assertThat(captor.getValue().getRecordedAt()).isEqualTo(NOW);
        verifyInboxRecorded();
    }

    @Test
    void orderCreatedDoesNotOverwriteExistingProjection() {
        when(orderCustomerRepository.existsById(ORDER_ID)).thenReturn(true);

        handler.onOrderCreated(EVENT_ID, ORDER_ID, CUSTOMER_ID);

        verify(orderCustomerRepository, never()).save(any());
        verifyInboxRecorded();
    }

    @Test
    void duplicateEventHasNoSideEffects() {
        when(processedEventRepository.existsById(EVENT_ID)).thenReturn(true);

        handler.onOrderCreated(EVENT_ID, ORDER_ID, CUSTOMER_ID);
        handler.onPaymentCompleted(EVENT_ID, ORDER_ID, new BigDecimal("25.00"), "USD");

        verify(orderCustomerRepository, never()).save(any());
        verifyNoInteractions(notificationService);
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void paymentCompletedSendsOrderConfirmationToProjectedCustomer() {
        givenProjection();

        handler.onPaymentCompleted(EVENT_ID, ORDER_ID, new BigDecimal("25.00"), "USD");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> variables = ArgumentCaptor.forClass(Map.class);
        verify(notificationService).sendNotification(eq(CUSTOMER_ID), eq(ORDER_ID),
            eq(NotificationType.ORDER_CONFIRMATION), eq(List.of(Channel.EMAIL, Channel.IN_APP)), variables.capture());
        assertThat(variables.getValue())
            .containsEntry("orderId", ORDER_ID.toString())
            .containsEntry("amount", "25.00")
            .containsEntry("currency", "USD");
        verifyInboxRecorded();
    }

    @Test
    void paymentBeforeProjectionThrowsSoBinderRetries() {
        when(orderCustomerRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> handler.onPaymentCompleted(EVENT_ID, ORDER_ID, BigDecimal.TEN, "USD"))
            .isInstanceOf(OrderCustomerNotYetKnownException.class);

        verifyNoInteractions(notificationService);
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void paymentFailedSendsPaymentFailedNotification() {
        givenProjection();

        handler.onPaymentFailed(EVENT_ID, ORDER_ID, "GATEWAY_DECLINED");

        verify(notificationService).sendNotification(eq(CUSTOMER_ID), eq(ORDER_ID),
            eq(NotificationType.PAYMENT_FAILED), anyList(), anyMap());
        verifyInboxRecorded();
    }

    @Test
    void paymentRefundedSendsRefundNotification() {
        givenProjection();

        handler.onPaymentRefunded(EVENT_ID, ORDER_ID, new BigDecimal("12.50"));

        verify(notificationService).sendNotification(eq(CUSTOMER_ID), eq(ORDER_ID),
            eq(NotificationType.REFUND_PROCESSED), eq(List.of(Channel.EMAIL)), anyMap());
    }

    @Test
    void prescriptionEventsNotifyCustomerDirectly() {
        handler.onPrescriptionVerified(EVENT_ID, PRESCRIPTION_ID, CUSTOMER_ID);
        verify(notificationService).sendNotification(eq(CUSTOMER_ID), eq(null),
            eq(NotificationType.PRESCRIPTION_VERIFIED), eq(List.of(Channel.IN_APP)), anyMap());

        handler.onPrescriptionRejected("another-event", PRESCRIPTION_ID, CUSTOMER_ID, null);
        verify(notificationService).sendNotification(eq(CUSTOMER_ID), eq(null),
            eq(NotificationType.PRESCRIPTION_REJECTED), eq(List.of(Channel.IN_APP)),
            eq(Map.of("prescriptionId", PRESCRIPTION_ID.toString(), "reasonCode", "UNSPECIFIED")));
    }

    private void givenProjection() {
        when(orderCustomerRepository.findById(ORDER_ID))
            .thenReturn(Optional.of(new OrderCustomer(ORDER_ID, CUSTOMER_ID, NOW)));
    }

    private void verifyInboxRecorded() {
        ArgumentCaptor<ProcessedEvent> captor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository).save(captor.capture());
        assertThat(captor.getValue().getEventId()).isEqualTo(EVENT_ID);
    }
}
