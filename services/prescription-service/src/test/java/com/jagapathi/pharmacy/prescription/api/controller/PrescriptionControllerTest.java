package com.jagapathi.pharmacy.prescription.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagapathi.pharmacy.prescription.api.request.CreatePrescriptionRequest;
import com.jagapathi.pharmacy.prescription.api.request.FillPrescriptionLineRequest;
import com.jagapathi.pharmacy.prescription.api.request.PrescriptionLineCreateRequest;
import com.jagapathi.pharmacy.prescription.api.response.PrescriptionLineResponse;
import com.jagapathi.pharmacy.prescription.api.response.PrescriptionResponse;
import com.jagapathi.pharmacy.prescription.api.response.PageResponse;
import com.jagapathi.pharmacy.prescription.application.service.PrescriptionService;
import com.jagapathi.pharmacy.prescription.domain.exception.PrescriptionNotFoundException;
import com.jagapathi.pharmacy.prescription.domain.model.PrescriptionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PrescriptionControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @MockBean
    private PrescriptionService prescriptionService;
    
    @Test
    @WithMockUser(roles = "CUSTOMER")
    void testGetPrescriptionSuccess() throws Exception {
        String prescriptionId = UUID.randomUUID().toString();
        PrescriptionResponse response = createTestPrescriptionResponse(prescriptionId);
        
        when(prescriptionService.getPrescription(prescriptionId)).thenReturn(response);
        
        mockMvc.perform(get("/api/v1/prescriptions/{id}", prescriptionId)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(prescriptionId))
            .andExpect(jsonPath("$.status").value("PENDING"));
        
        verify(prescriptionService).getPrescription(prescriptionId);
    }
    
    @Test
    @WithMockUser(roles = "CUSTOMER")
    void testGetPrescriptionNotFound() throws Exception {
        String prescriptionId = UUID.randomUUID().toString();
        
        when(prescriptionService.getPrescription(prescriptionId))
            .thenThrow(new PrescriptionNotFoundException(prescriptionId));
        
        mockMvc.perform(get("/api/v1/prescriptions/{id}", prescriptionId)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound());
    }
    
    @Test
    void testGetPrescriptionUnauthorized() throws Exception {
        String prescriptionId = UUID.randomUUID().toString();
        
        mockMvc.perform(get("/api/v1/prescriptions/{id}", prescriptionId)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }
    
    @Test
    @WithMockUser(roles = "CUSTOMER")
    void testListByStatus() throws Exception {
        String customerId = UUID.randomUUID().toString();
        PrescriptionResponse response = createTestPrescriptionResponse(UUID.randomUUID().toString());
        PageResponse<PrescriptionResponse> pageResponse = new PageResponse<>(
            List.of(response),
            0,
            20,
            1,
            1
        );
        
        when(prescriptionService.listByStatus(eq(PrescriptionStatus.ACTIVE), any()))
            .thenReturn(pageResponse);
        
        mockMvc.perform(get("/api/v1/prescriptions")
                .param("status", "ACTIVE")
                .param("page", "0")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }
    
    @Test
    @WithMockUser(roles = "CUSTOMER")
    void testListByCustomerId() throws Exception {
        String customerId = UUID.randomUUID().toString();
        PrescriptionResponse response = createTestPrescriptionResponse(UUID.randomUUID().toString());
        PageResponse<PrescriptionResponse> pageResponse = new PageResponse<>(
            List.of(response),
            0,
            20,
            1,
            1
        );
        
        when(prescriptionService.listByCustomerId(eq(customerId), any()))
            .thenReturn(pageResponse);
        
        mockMvc.perform(get("/api/v1/prescriptions/customers/{customerId}", customerId)
                .param("page", "0")
                .param("size", "20")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1));
    }
    
    @Test
    @WithMockUser(roles = "PHARMACIST")
    void testCreatePrescriptionSuccess() throws Exception {
        String customerId = UUID.randomUUID().toString();
        String prescriberId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        
        CreatePrescriptionRequest request = new CreatePrescriptionRequest(
            customerId,
            prescriberId,
            now,
            now.plusSeconds(30 * 24 * 3600),
            List.of(
                new PrescriptionLineCreateRequest(
                    UUID.randomUUID().toString(),
                    BigDecimal.valueOf(2),
                    "Test"
                )
            )
        );
        
        PrescriptionResponse response = createTestPrescriptionResponse(UUID.randomUUID().toString());
        
        when(prescriptionService.createPrescription(any())).thenReturn(response);
        
        mockMvc.perform(post("/api/v1/prescriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated());
    }
    
    @Test
    @WithMockUser(roles = "CUSTOMER")
    void testCreatePrescriptionForbidden() throws Exception {
        String customerId = UUID.randomUUID().toString();
        String prescriberId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        
        CreatePrescriptionRequest request = new CreatePrescriptionRequest(
            customerId,
            prescriberId,
            now,
            now.plusSeconds(30 * 24 * 3600),
            List.of(
                new PrescriptionLineCreateRequest(
                    UUID.randomUUID().toString(),
                    BigDecimal.valueOf(2),
                    "Test"
                )
            )
        );
        
        mockMvc.perform(post("/api/v1/prescriptions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden());
    }
    
    @Test
    @WithMockUser(roles = "PHARMACIST")
    void testActivatePrescription() throws Exception {
        String prescriptionId = UUID.randomUUID().toString();
        PrescriptionResponse response = createTestPrescriptionResponse(prescriptionId);
        
        when(prescriptionService.activatePrescription(prescriptionId)).thenReturn(response);
        
        mockMvc.perform(post("/api/v1/prescriptions/{id}/activate", prescriptionId)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());
    }
    
    @Test
    @WithMockUser(roles = "PHARMACIST")
    void testFillPrescriptionLine() throws Exception {
        String prescriptionId = UUID.randomUUID().toString();
        String lineId = UUID.randomUUID().toString();
        
        FillPrescriptionLineRequest request = new FillPrescriptionLineRequest(BigDecimal.valueOf(2));
        PrescriptionResponse response = createTestPrescriptionResponse(prescriptionId);
        
        when(prescriptionService.fillPrescriptionLine(prescriptionId, lineId, BigDecimal.valueOf(2)))
            .thenReturn(response);
        
        mockMvc.perform(put("/api/v1/prescriptions/{id}/lines/{lineId}/fill", prescriptionId, lineId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk());
    }
    
    private PrescriptionResponse createTestPrescriptionResponse(String prescriptionId) {
        return new PrescriptionResponse(
            prescriptionId,
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            Instant.now(),
            Instant.now().plusSeconds(30 * 24 * 3600),
            "PENDING",
            0,
            Instant.now(),
            Instant.now(),
            List.of()
        );
    }
}
