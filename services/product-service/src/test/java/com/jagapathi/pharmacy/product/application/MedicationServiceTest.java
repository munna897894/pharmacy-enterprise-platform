package com.jagapathi.pharmacy.product.application.service;

import com.jagapathi.pharmacy.product.api.response.MedicationResponse;
import com.jagapathi.pharmacy.product.domain.exception.DuplicateNdcCodeException;
import com.jagapathi.pharmacy.product.domain.exception.MedicationNotFoundException;
import com.jagapathi.pharmacy.product.domain.model.Medication;
import com.jagapathi.pharmacy.product.domain.repository.MedicationRepository;
import com.jagapathi.pharmacy.product.application.mapper.MedicationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
        service = new MedicationService(repository, mapper);
    }

    @Test
    void should_create_medication_successfully() {
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
    void should_throw_exception_when_medication_not_found() {
        when(repository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMedicationById("nonexistent"))
                .isInstanceOf(MedicationNotFoundException.class);
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
    void should_deactivate_medication() {
        Medication med = new Medication("0001", "Ibuprofen", "Ibuprofen", "Pharma", "Tablet", "200mg", BigDecimal.valueOf(9.99), "USD");
        MedicationResponse response = new MedicationResponse(med.getId(), "0001", "Ibuprofen", "Ibuprofen", "Pharma", "Tablet", "200mg", BigDecimal.valueOf(9.99), "USD", false, 1, med.getCreatedAt(), med.getUpdatedAt());

        when(repository.findById(med.getId())).thenReturn(Optional.of(med));
        when(repository.save(any(Medication.class))).thenReturn(med);
        when(mapper.toResponse(med)).thenReturn(response);

        MedicationResponse result = service.updateMedicationStatus(med.getId(), false);

        assertThat(result).isNotNull();
        verify(repository).findById(med.getId());
        verify(repository).save(any(Medication.class));
    }
}
