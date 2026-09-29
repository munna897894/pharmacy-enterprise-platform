package com.jagapathi.pharmacy.notification.infrastructure.messaging;

import com.jagapathi.pharmacy.notification.application.NotificationService;
import com.jagapathi.pharmacy.notification.domain.ProcessedEvent;
import com.jagapathi.pharmacy.notification.infrastructure.persistence.ProcessedEventRepository;
import com.jagapathi.pharmacy.platform.events.DomainEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrescriptionEventConsumerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private ProcessedEventRepository processedEventRepository;

    private PrescriptionEventConsumer prescriptionEventConsumer;

    @BeforeEach
    void setUp() {
        prescriptionEventConsumer = new PrescriptionEventConsumer(notificationService, processedEventRepository);
    }

    @Test
    void testHandlePrescriptionFilledEvent() {
        UUID eventId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID medicationId = UUID.randomUUID();
        UUID pharmacyId = UUID.randomUUID();

        PrescriptionFilledPayload payload = new PrescriptionFilledPayload(
                prescriptionId, customerId, medicationId, 30, pharmacyId, Instant.now()
        );

        DomainEvent<PrescriptionFilledPayload> event = new DomainEvent<>(
                eventId, "PrescriptionFilled", 1, "Prescription", prescriptionId,
                Instant.now(), "prescription-service", UUID.randomUUID(),
                null, "system", payload
        );

        when(processedEventRepository.existsById(eventId.toString())).thenReturn(false);
        doNothing().when(notificationService).sendNotification(any(), any(), any(), any());
        when(processedEventRepository.save(any())).thenReturn(new ProcessedEvent(eventId.toString()));

        prescriptionEventConsumer.handlePrescriptionFilled(event);

        verify(notificationService).sendNotification(any(), any(), any(), any());
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void testHandlePrescriptionExpiringEvent() {
        UUID eventId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID medicationId = UUID.randomUUID();

        PrescriptionExpiringPayload payload = new PrescriptionExpiringPayload(
                prescriptionId, customerId, medicationId, "Lisinopril",
                Instant.now().plusSeconds(86400)
        );

        DomainEvent<PrescriptionExpiringPayload> event = new DomainEvent<>(
                eventId, "PrescriptionExpiring", 1, "Prescription", prescriptionId,
                Instant.now(), "prescription-service", UUID.randomUUID(),
                null, "system", payload
        );

        when(processedEventRepository.existsById(eventId.toString())).thenReturn(false);
        doNothing().when(notificationService).sendNotification(any(), any(), any(), any());
        when(processedEventRepository.save(any())).thenReturn(new ProcessedEvent(eventId.toString()));

        prescriptionEventConsumer.handlePrescriptionExpiring(event);

        verify(notificationService).sendNotification(any(), any(), any(), any());
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void testDuplicateEventHandling() {
        UUID eventId = UUID.randomUUID();
        UUID prescriptionId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID medicationId = UUID.randomUUID();
        UUID pharmacyId = UUID.randomUUID();

        PrescriptionFilledPayload payload = new PrescriptionFilledPayload(
                prescriptionId, customerId, medicationId, 30, pharmacyId, Instant.now()
        );

        DomainEvent<PrescriptionFilledPayload> event = new DomainEvent<>(
                eventId, "PrescriptionFilled", 1, "Prescription", prescriptionId,
                Instant.now(), "prescription-service", UUID.randomUUID(),
                null, "system", payload
        );

        when(processedEventRepository.existsById(eventId.toString())).thenReturn(true);

        prescriptionEventConsumer.handlePrescriptionFilled(event);

        verify(notificationService, never()).sendNotification(any(), any(), any(), any());
        verify(processedEventRepository, never()).save(any());
    }
}
