package com.jagapathi.pharmacy.product.application.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.jagapathi.pharmacy.product.api.response.MedicationResponse;
import com.jagapathi.pharmacy.product.domain.exception.DuplicateNdcCodeException;
import com.jagapathi.pharmacy.product.domain.exception.MedicationNotFoundException;
import com.jagapathi.pharmacy.product.domain.model.Medication;
import com.jagapathi.pharmacy.product.domain.repository.MedicationRepository;
import com.jagapathi.pharmacy.product.application.mapper.MedicationMapper;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MedicationServiceTest {

    private MedicationService service;

    @Mock
    private MedicationRepository repository;

    @Mock
    private MedicationMapper mapper;

    @BeforeEach
    void setup() {
        service = new MedicationService(repository, mapper, io.micrometer.observation.ObservationRegistry.create());
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void should_create_medication_successfully(CapturedOutput output) {
        Medication saved = new Medication("0001", "Ibuprofen", "Ibuprofen", "Pharma", "Tablet", "200mg", BigDecimal.valueOf(9.99), "USD");
        MedicationResponse response = new MedicationResponse(saved.getId(), "0001", "Ibuprofen", "Ibuprofen", "Pharma", "Tablet", "200mg", BigDecimal.valueOf(9.99), "USD", true, 0, saved.getCreatedAt(), saved.getUpdatedAt());

        when(repository.findByNdcCode("0001")).thenReturn(Optional.empty());
        when(repository.save(any(Medication.class))).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(response);

        MedicationResponse result = service.createMedication("0001", "Ibuprofen", "Ibuprofen", "Pharma", "Tablet", "200mg", BigDecimal.valueOf(9.99), "USD");

        assertThat(result).isNotNull();
        assertThat(result.ndcCode()).isEqualTo("0001");
        verify(repository).findByNdcCode("0001");
        verify(repository).save(any(Medication.class));
        assertThat(output).contains("medication.created medicationId=" + saved.getId() + " outcome=created")
                .doesNotContain("0001", "Ibuprofen");
    }

    @Test
    void should_throw_exception_when_ndc_code_duplicate() {
        Medication existing = new Medication("0001", "Ibuprofen", "Ibuprofen", "Pharma", "Tablet", "200mg", BigDecimal.valueOf(9.99), "USD");
        when(repository.findByNdcCode("0001")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createMedication("0001", "Ibuprofen", "Ibuprofen", "Pharma", "Tablet", "200mg", BigDecimal.valueOf(9.99), "USD"))
                .isInstanceOf(DuplicateNdcCodeException.class);

        verify(repository).findByNdcCode("0001");
        verify(repository, never()).save(any());
    }

    @Test
    void should_get_medication_by_id() {
        Medication med = new Medication("0001", "Ibuprofen", "Ibuprofen", "Pharma", "Tablet", "200mg", BigDecimal.valueOf(9.99), "USD");
        MedicationResponse response = new MedicationResponse(med.getId(), "0001", "Ibuprofen", "Ibuprofen", "Pharma", "Tablet", "200mg", BigDecimal.valueOf(9.99), "USD", true, 0, med.getCreatedAt(), med.getUpdatedAt());

        when(repository.findById(med.getId())).thenReturn(Optional.of(med));
        when(mapper.toResponse(med)).thenReturn(response);

        MedicationResponse result = service.getMedicationById(med.getId());

        assertThat(result).isNotNull();
        assertThat(result.ndcCode()).isEqualTo("0001");
    }

    @Test
    void should_log_database_lookup_at_debug_only_without_catalog_content() {
        Medication med = new Medication("0001", "Ibuprofen", "Ibuprofen", "Pharma", "Tablet",
                "200mg", BigDecimal.valueOf(9.99), "USD");
        when(repository.findById(med.getId())).thenReturn(Optional.of(med));
        Logger logger = (Logger) LoggerFactory.getLogger(MedicationService.class);
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        try {
            service.getMedicationById(med.getId());

            assertThat(appender.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.DEBUG);
                assertThat(event.getFormattedMessage())
                        .isEqualTo("medication.lookup.completed medicationId=" + med.getId() + " outcome=found")
                        .doesNotContain("0001", "Ibuprofen");
            });
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }
    }

    @Test
    void should_throw_exception_when_medication_not_found() {
        when(repository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMedicationById("nonexistent"))
                .isInstanceOf(MedicationNotFoundException.class);
    }

    @Test
    void should_observe_catalog_and_repository_search_without_query_in_span_names() {
        ObservationRegistry registry = ObservationRegistry.create();
        List<String> spanNames = new ArrayList<>();
        registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            @Override
            public void onStart(Observation.Context context) {
                spanNames.add(context.getName());
            }

            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }
        });
        MedicationService observedService = new MedicationService(repository, mapper, registry);
        PageRequest pageable = PageRequest.of(0, 2);
        when(repository.searchMedications("synthetic-search", null, null, null, pageable))
                .thenReturn(Page.empty(pageable));

        observedService.searchMedications("synthetic-search", null, null, null, pageable);

        assertThat(spanNames).containsExactly("medication.catalog.search", "medication.repository.search");
        assertThat(spanNames).noneMatch(name -> name.contains("synthetic-search"));
    }

    @Test
    void should_update_medication() {
        Medication med = new Medication("0001", "Ibuprofen", "Ibuprofen", "Pharma", "Tablet", "200mg", BigDecimal.valueOf(9.99), "USD");
        MedicationResponse response = new MedicationResponse(med.getId(), "0001", "Ibuprofen Plus", "Ibuprofen", "New Pharma", "Tablet", "250mg", BigDecimal.valueOf(10.99), "USD", true, 1, med.getCreatedAt(), med.getUpdatedAt());

        when(repository.findById(med.getId())).thenReturn(Optional.of(med));
        when(repository.save(any(Medication.class))).thenReturn(med);
        when(mapper.toResponse(med)).thenReturn(response);

        MedicationResponse result = service.updateMedication(med.getId(), "Ibuprofen Plus", "Ibuprofen", "New Pharma", "Tablet", "250mg", BigDecimal.valueOf(10.99));

        assertThat(result).isNotNull();
        verify(repository).findById(med.getId());
        verify(repository).save(any(Medication.class));
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void should_deactivate_medication(CapturedOutput output) {
        Medication med = new Medication("0001", "Ibuprofen", "Ibuprofen", "Pharma", "Tablet", "200mg", BigDecimal.valueOf(9.99), "USD");
        MedicationResponse response = new MedicationResponse(med.getId(), "0001", "Ibuprofen", "Ibuprofen", "Pharma", "Tablet", "200mg", BigDecimal.valueOf(9.99), "USD", false, 1, med.getCreatedAt(), med.getUpdatedAt());

        when(repository.findById(med.getId())).thenReturn(Optional.of(med));
        when(repository.save(any(Medication.class))).thenReturn(med);
        when(mapper.toResponse(med)).thenReturn(response);

        MedicationResponse result = service.updateMedicationStatus(med.getId(), false);

        assertThat(result).isNotNull();
        verify(repository).findById(med.getId());
        verify(repository).save(any(Medication.class));
        assertThat(output).contains("medication.status.updated medicationId=" + med.getId() + " active=false")
                .doesNotContain("Ibuprofen", "0001");
    }
}
