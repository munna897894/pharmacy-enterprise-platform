package com.jagapathi.pharmacy.prescription.application.service;

import com.jagapathi.pharmacy.prescription.api.request.CreatePrescriptionRequest;
import com.jagapathi.pharmacy.prescription.api.request.PrescriptionLineCreateRequest;
import com.jagapathi.pharmacy.prescription.api.response.PrescriptionResponse;
import com.jagapathi.pharmacy.prescription.api.response.PageResponse;
import com.jagapathi.pharmacy.prescription.application.mapper.PrescriptionMapper;
import com.jagapathi.pharmacy.prescription.domain.exception.InvalidPrescriptionStateException;
import com.jagapathi.pharmacy.prescription.domain.exception.PrescriptionNotFoundException;
import com.jagapathi.pharmacy.prescription.domain.model.Prescription;
import com.jagapathi.pharmacy.prescription.domain.model.PrescriptionLine;
import com.jagapathi.pharmacy.prescription.domain.model.PrescriptionStatus;
import com.jagapathi.pharmacy.prescription.domain.repository.PrescriptionRepository;
import com.jagapathi.pharmacy.prescription.domain.repository.PrescriptionLineRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PrescriptionServiceTest {
    
    @Mock
    private PrescriptionRepository prescriptionRepository;
    
    @Mock
    private PrescriptionLineRepository prescriptionLineRepository;
    
    @Mock
    private PrescriptionMapper prescriptionMapper;
    
    @Mock
    private RestTemplate restTemplate;
    
    @InjectMocks
    private PrescriptionService prescriptionService;
    
    @Test
    void testCreatePrescription() {
        String customerId = UUID.randomUUID().toString();
        String prescriberId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant futureTime = now.plusSeconds(3600); // 1 hour in the future
        Instant expiresAt = futureTime.plusSeconds(30 * 24 * 3600);
        
        PrescriptionLineCreateRequest lineRequest = new PrescriptionLineCreateRequest(
            UUID.randomUUID().toString(),
            BigDecimal.valueOf(2),
            "Test instructions"
        );
        
        CreatePrescriptionRequest request = new CreatePrescriptionRequest(
            customerId,
            prescriberId,
            futureTime,
            expiresAt,
            List.of(lineRequest)
        );
        
        Prescription expectedPrescription = new Prescription(customerId, prescriberId, futureTime, expiresAt);
        PrescriptionResponse expectedResponse = new PrescriptionResponse(
            expectedPrescription.getId(),
            customerId,
            prescriberId,
            futureTime,
            expiresAt,
            "PENDING",
            0,
            expectedPrescription.getCreatedAt(),
            expectedPrescription.getUpdatedAt(),
            List.of()
        );
        
        when(prescriptionRepository.save(any(Prescription.class))).thenReturn(expectedPrescription);
        when(prescriptionMapper.toResponse(expectedPrescription)).thenReturn(expectedResponse);
        
        PrescriptionResponse response = prescriptionService.createPrescription(request);
        
        assertThat(response).isNotNull();
        assertThat(response.customerId()).isEqualTo(customerId);
        assertThat(response.status()).isEqualTo("PENDING");
        verify(prescriptionRepository).save(any(Prescription.class));
    }
    
    @Test
    void testGetPrescription() {
        String prescriptionId = UUID.randomUUID().toString();
        Prescription prescription = new Prescription(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            Instant.now(),
            Instant.now().plusSeconds(30 * 24 * 3600)
        );
        
        when(prescriptionRepository.findById(prescriptionId)).thenReturn(Optional.of(prescription));
        when(prescriptionMapper.toResponse(prescription)).thenReturn(
            new PrescriptionResponse(
                prescription.getId(),
                prescription.getCustomerId(),
                prescription.getPrescriberId(),
                prescription.getPrescribedAt(),
                prescription.getExpiresAt(),
                "PENDING",
                0,
                prescription.getCreatedAt(),
                prescription.getUpdatedAt(),
                List.of()
            )
        );
        
        PrescriptionResponse response = prescriptionService.getPrescription(prescriptionId);
        
        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(prescription.getId());
    }
    
    @Test
    void testGetPrescriptionNotFound() {
        String prescriptionId = UUID.randomUUID().toString();
        
        when(prescriptionRepository.findById(prescriptionId)).thenReturn(Optional.empty());
        
        assertThatThrownBy(() -> prescriptionService.getPrescription(prescriptionId))
            .isInstanceOf(PrescriptionNotFoundException.class);
    }
    
    @Test
    void testListByCustomerId() {
        String customerId = UUID.randomUUID().toString();
        Pageable pageable = PageRequest.of(0, 20);
        
        Prescription prescription = new Prescription(
            customerId,
            UUID.randomUUID().toString(),
            Instant.now(),
            Instant.now().plusSeconds(30 * 24 * 3600)
        );
        
        Page<Prescription> prescriptionPage = new PageImpl<>(List.of(prescription));
        
        when(prescriptionRepository.findByCustomerId(customerId, pageable)).thenReturn(prescriptionPage);
        when(prescriptionMapper.toResponse(prescription)).thenReturn(
            new PrescriptionResponse(
                prescription.getId(),
                customerId,
                prescription.getPrescriberId(),
                prescription.getPrescribedAt(),
                prescription.getExpiresAt(),
                "PENDING",
                0,
                prescription.getCreatedAt(),
                prescription.getUpdatedAt(),
                List.of()
            )
        );
        
        PageResponse<PrescriptionResponse> response = prescriptionService.listByCustomerId(customerId, pageable);
        
        assertThat(response).isNotNull();
        assertThat(response.content()).hasSize(1);
    }
    
    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void testActivatePrescription(CapturedOutput output) {
        String prescriptionId = UUID.randomUUID().toString();
        Prescription prescription = new Prescription(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            Instant.now(),
            Instant.now().plusSeconds(30 * 24 * 3600)
        );
        
        when(prescriptionRepository.findById(prescriptionId)).thenReturn(Optional.of(prescription));
        when(prescriptionRepository.save(any(Prescription.class))).thenReturn(prescription);
        when(prescriptionMapper.toResponse(prescription)).thenReturn(
            new PrescriptionResponse(
                prescription.getId(),
                prescription.getCustomerId(),
                prescription.getPrescriberId(),
                prescription.getPrescribedAt(),
                prescription.getExpiresAt(),
                "ACTIVE",
                0,
                prescription.getCreatedAt(),
                prescription.getUpdatedAt(),
                List.of()
            )
        );
        
        PrescriptionResponse response = prescriptionService.activatePrescription(prescriptionId);
        
        assertThat(response).isNotNull();
        verify(prescriptionRepository).save(any(Prescription.class));
        assertThat(output).contains("prescription.verification.completed prescriptionId="
                + prescription.getId() + " outcome=activated")
                .doesNotContain(prescription.getCustomerId(), prescription.getPrescriberId());
    }
    
    @Test
    void testActivateNonPendingThrowsException() {
        String prescriptionId = UUID.randomUUID().toString();
        Prescription prescription = new Prescription(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            Instant.now(),
            Instant.now().plusSeconds(30 * 24 * 3600)
        );
        prescription.activate();
        
        when(prescriptionRepository.findById(prescriptionId)).thenReturn(Optional.of(prescription));
        
        assertThatThrownBy(() -> prescriptionService.activatePrescription(prescriptionId))
            .isInstanceOf(InvalidPrescriptionStateException.class);
    }
    
    @Test
    void testFillPrescriptionLine() {
        String prescriptionId = UUID.randomUUID().toString();
        String lineId = UUID.randomUUID().toString();
        
        Prescription prescription = new Prescription(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            Instant.now(),
            Instant.now().plusSeconds(30 * 24 * 3600)
        );
        prescription.activate();
        
        PrescriptionLine line = new PrescriptionLine(prescriptionId, UUID.randomUUID().toString(), BigDecimal.valueOf(5), null);
        
        when(prescriptionRepository.findById(prescriptionId)).thenReturn(Optional.of(prescription));
        when(prescriptionLineRepository.findById(lineId)).thenReturn(Optional.of(line));
        when(prescriptionRepository.save(any(Prescription.class))).thenReturn(prescription);
        when(prescriptionMapper.toResponse(prescription)).thenReturn(
            new PrescriptionResponse(
                prescription.getId(),
                prescription.getCustomerId(),
                prescription.getPrescriberId(),
                prescription.getPrescribedAt(),
                prescription.getExpiresAt(),
                "ACTIVE",
                0,
                prescription.getCreatedAt(),
                prescription.getUpdatedAt(),
                List.of()
            )
        );
        
        PrescriptionResponse response = prescriptionService.fillPrescriptionLine(prescriptionId, lineId, BigDecimal.valueOf(5));
        
        assertThat(response).isNotNull();
        verify(prescriptionLineRepository).save(any(PrescriptionLine.class));
    }
    
    @Test
    void testExpirePrescriptions() {
        Instant now = Instant.now();
        Instant pastTime = now.minusSeconds(7 * 24 * 3600);
        Instant expiredAt = pastTime.minusSeconds(3600);
        
        Prescription prescription = new Prescription(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            pastTime,
            expiredAt
        );
        prescription.activate();
        
        when(prescriptionRepository.findExpiredPrescriptions(eq(PrescriptionStatus.ACTIVE), any(Instant.class)))
            .thenReturn(List.of(prescription));
        when(prescriptionRepository.save(any(Prescription.class))).thenReturn(prescription);
        
        prescriptionService.expirePrescriptions();
        
        assertThat(prescription.getStatus()).isEqualTo(PrescriptionStatus.EXPIRED);
        verify(prescriptionRepository).save(any(Prescription.class));
    }
}
