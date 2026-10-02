package com.jagapathi.pharmacy.inventory.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jagapathi.pharmacy.inventory.domain.model.InventoryReservation;
import com.jagapathi.pharmacy.inventory.domain.model.ReservationItem;
import com.jagapathi.pharmacy.inventory.domain.model.StockLevel;
import com.jagapathi.pharmacy.inventory.domain.repository.InventoryReservationRepository;
import com.jagapathi.pharmacy.inventory.domain.repository.StockLevelRepository;
import com.jagapathi.pharmacy.inventory.infrastructure.OutboxEvent;
import com.jagapathi.pharmacy.inventory.infrastructure.OutboxEventRepository;
import com.jagapathi.pharmacy.inventory.infrastructure.ProcessedEventRepository;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventorySagaServiceTest {

    @Mock private StockLevelRepository stockLevelRepository;
    @Mock private InventoryReservationRepository reservationRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private ProcessedEventRepository processedEventRepository;
    @Mock private KafkaTemplate<String, String> kafkaTemplate;

    private InventorySagaService sagaService;

    private static final String PHARMACY_ID = "p1000000-0000-0000-0000-000000000001";
    private static final String PRODUCT_ID = "550e8400-e29b-41d4-a716-446655440001";

    @BeforeEach
    void setUp() {
        sagaService = new InventorySagaService(stockLevelRepository, reservationRepository,
            outboxEventRepository, processedEventRepository, new ObjectMapper().registerModule(new JavaTimeModule()), kafkaTemplate);
    }

    @Test
    void reservesStockAndPublishesInventoryReservedWhenAvailable() {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        StockLevel stock = new StockLevel(PHARMACY_ID, PRODUCT_ID, BigDecimal.valueOf(100), BigDecimal.TEN, BigDecimal.TEN);

        when(processedEventRepository.existsByOrderIdAndEventId(orderId, eventId)).thenReturn(false);
        when(reservationRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(stockLevelRepository.findByPharmacyIdAndProductId(PHARMACY_ID, PRODUCT_ID)).thenReturn(Optional.of(stock));

        var logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(InventorySagaService.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            sagaService.handleOrderCreated(eventId, orderId, PHARMACY_ID,
                List.of(new ReservationItem(PRODUCT_ID, BigDecimal.valueOf(5))), Instant.now());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertThat(stock.getQuantityOnHand()).isEqualByComparingTo(BigDecimal.valueOf(95));
        verify(reservationRepository).save(any(InventoryReservation.class));
        assertThat(appender.list).anySatisfy(event -> {
            Map<String, Object> fields = event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
            assertThat(fields)
                .containsEntry("eventName", "inventory.reservation.completed")
                .containsEntry("orderId", orderId)
                .containsEntry("eventId", eventId)
                .containsKey("resultEventId")
                .doesNotContainKeys("productId", "quantity", "pharmacyId");
        });

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("InventoryReserved");
        assertThat(outboxCaptor.getValue().getPayload()).contains("InventoryReserved");
    }

    @Test
    void rejectsWhenStockInsufficientWithoutMutatingStock() {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        StockLevel stock = new StockLevel(PHARMACY_ID, PRODUCT_ID, BigDecimal.valueOf(2), BigDecimal.TEN, BigDecimal.TEN);

        when(processedEventRepository.existsByOrderIdAndEventId(orderId, eventId)).thenReturn(false);
        when(reservationRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(stockLevelRepository.findByPharmacyIdAndProductId(PHARMACY_ID, PRODUCT_ID)).thenReturn(Optional.of(stock));

        var logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(InventorySagaService.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        try {
            sagaService.handleOrderCreated(eventId, orderId, PHARMACY_ID,
                List.of(new ReservationItem(PRODUCT_ID, BigDecimal.valueOf(5))), Instant.now());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        assertThat(stock.getQuantityOnHand()).isEqualByComparingTo(BigDecimal.valueOf(2));
        verify(reservationRepository, never()).save(any());
        assertThat(appender.list).anySatisfy(event -> {
            Map<String, Object> fields = event.getKeyValuePairs().stream()
                .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
            assertThat(fields)
                .containsEntry("eventName", "inventory.reservation.rejected")
                .containsEntry("orderId", orderId)
                .containsEntry("eventId", eventId)
                .containsKey("resultEventId")
                .containsEntry("failureCode", "INSUFFICIENT_STOCK")
                .doesNotContainKeys("productId", "quantity", "pharmacyId");
        });

        ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo("InventoryRejected");
    }

    @Test
    void skipsDuplicateOrderCreatedEventDelivery() {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        when(processedEventRepository.existsByOrderIdAndEventId(orderId, eventId)).thenReturn(true);

        sagaService.handleOrderCreated(eventId, orderId, PHARMACY_ID,
            List.of(new ReservationItem(PRODUCT_ID, BigDecimal.ONE)), Instant.now());

        verifyNoInteractions(stockLevelRepository, reservationRepository, outboxEventRepository);
    }

    @Test
    void releasesStockAndReservationOnPaymentFailed() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        String itemsJson = mapper.writeValueAsString(List.of(new ReservationItem(PRODUCT_ID, BigDecimal.valueOf(5))));
        InventoryReservation reservation = new InventoryReservation(orderId, PHARMACY_ID, itemsJson);
        StockLevel stock = new StockLevel(PHARMACY_ID, PRODUCT_ID, BigDecimal.valueOf(95), BigDecimal.TEN, BigDecimal.TEN);

        when(processedEventRepository.existsByOrderIdAndEventId(orderId, eventId)).thenReturn(false);
        when(reservationRepository.findByOrderId(orderId)).thenReturn(Optional.of(reservation));
        when(stockLevelRepository.findByPharmacyIdAndProductId(PHARMACY_ID, PRODUCT_ID)).thenReturn(Optional.of(stock));

        sagaService.handlePaymentFailed(eventId, orderId);

        assertThat(stock.getQuantityOnHand()).isEqualByComparingTo(BigDecimal.valueOf(100));
        assertThat(reservation.getStatus()).isEqualTo("RELEASED");
        verify(reservationRepository).save(reservation);
    }

    @Test
    void commitsReservationOnPaymentCompletedWithoutMutatingStock() {
        UUID orderId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        InventoryReservation reservation = new InventoryReservation(orderId, PHARMACY_ID, "[]");

        when(processedEventRepository.existsByOrderIdAndEventId(orderId, eventId)).thenReturn(false);
        when(reservationRepository.findByOrderId(orderId)).thenReturn(Optional.of(reservation));

        sagaService.handlePaymentCompleted(eventId, orderId);

        assertThat(reservation.getStatus()).isEqualTo("COMMITTED");
        verifyNoInteractions(stockLevelRepository);
        verify(reservationRepository).save(reservation);
    }
}
