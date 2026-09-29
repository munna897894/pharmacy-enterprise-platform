package com.jagapathi.pharmacy.product.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagapathi.pharmacy.product.api.request.CreateMedicationRequest;
import com.jagapathi.pharmacy.product.api.response.MedicationResponse;
import com.jagapathi.pharmacy.product.application.service.MedicationService;
import com.jagapathi.pharmacy.product.domain.exception.MedicationNotFoundException;
import com.jagapathi.pharmacy.product.infrastructure.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MedicationController.class)
@ActiveProfiles("test")
@Import(TestSecurityConfig.class)
class MedicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MedicationService medicationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void should_get_medication_by_id() throws Exception {
        MedicationResponse response = new MedicationResponse(
                "123", "00001", "Ibuprofen", "Ibuprofen", "Pharma", "Tablet", "200mg",
                BigDecimal.valueOf(9.99), "USD", true, 1, Instant.now(), Instant.now()
        );
        when(medicationService.getMedicationById("123")).thenReturn(response);

        mockMvc.perform(get("/api/v1/medications/123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("123"))
                .andExpect(jsonPath("$.name").value("Ibuprofen"));

        verify(medicationService).getMedicationById("123");
    }

    @Test
    void should_return_404_for_nonexistent_medication() throws Exception {
        when(medicationService.getMedicationById("nonexistent"))
                .thenThrow(new MedicationNotFoundException("nonexistent"));

        mockMvc.perform(get("/api/v1/medications/nonexistent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("MEDICATION_NOT_FOUND"));
    }

    @Test
    void should_search_medications() throws Exception {
        mockMvc.perform(get("/api/v1/medications")
                .param("q", "ibuprofen")
                .param("page", "0")
                .param("size", "20"))
                .andExpect(status().isOk());
    }

    @Test
    void should_create_medication() throws Exception {
        CreateMedicationRequest request = new CreateMedicationRequest(
                "00001", "Ibuprofen", "Ibuprofen", "Pharma", "Tablet", "200mg", BigDecimal.valueOf(9.99), "USD"
        );

        MedicationResponse response = new MedicationResponse(
                "123", "00001", "Ibuprofen", "Ibuprofen", "Pharma", "Tablet", "200mg",
                BigDecimal.valueOf(9.99), "USD", true, 1, Instant.now(), Instant.now()
        );
        when(medicationService.createMedication(
                "00001", "Ibuprofen", "Ibuprofen", "Pharma", "Tablet", "200mg", BigDecimal.valueOf(9.99), "USD"
        )).thenReturn(response);

        mockMvc.perform(post("/api/v1/medications")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("123"));
    }

    @Test
    void should_validate_create_request() throws Exception {
        CreateMedicationRequest invalidRequest = new CreateMedicationRequest(
                "0001", "Ibuprofen", "Ibuprofen", "Pharma", "Tablet", "200mg", BigDecimal.valueOf(9.99), "USD"
        );

        mockMvc.perform(post("/api/v1/medications")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }
}
