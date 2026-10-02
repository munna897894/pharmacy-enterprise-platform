package com.jagapathi.pharmacy.prescription.application.service;

import com.jagapathi.pharmacy.observability.BusinessMetric;
import com.jagapathi.pharmacy.observability.RecordBusinessMetric;
import com.jagapathi.pharmacy.prescription.api.request.CreatePrescriptionRequest;
import com.jagapathi.pharmacy.prescription.api.request.PrescriptionLineCreateRequest;
import com.jagapathi.pharmacy.prescription.api.response.PrescriptionResponse;
import com.jagapathi.pharmacy.prescription.api.response.PageResponse;
import com.jagapathi.pharmacy.prescription.application.mapper.PrescriptionMapper;
import com.jagapathi.pharmacy.prescription.domain.exception.*;
import com.jagapathi.pharmacy.prescription.domain.model.Prescription;
import com.jagapathi.pharmacy.prescription.domain.model.PrescriptionLine;
import com.jagapathi.pharmacy.prescription.domain.model.PrescriptionStatus;
import com.jagapathi.pharmacy.prescription.domain.repository.PrescriptionRepository;
import com.jagapathi.pharmacy.prescription.domain.repository.PrescriptionLineRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;

@Service
@Transactional
public class PrescriptionService {
    
    private static final Logger log = LoggerFactory.getLogger(PrescriptionService.class);
    
    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionLineRepository prescriptionLineRepository;
    private final PrescriptionMapper prescriptionMapper;
    private final RestTemplate restTemplate;

    public PrescriptionService(PrescriptionRepository prescriptionRepository,
                               PrescriptionLineRepository prescriptionLineRepository,
                               PrescriptionMapper prescriptionMapper,
                               RestTemplate restTemplate) {
        this.prescriptionRepository = prescriptionRepository;
        this.prescriptionLineRepository = prescriptionLineRepository;
        this.prescriptionMapper = prescriptionMapper;
        this.restTemplate = restTemplate;
    }

    public PrescriptionResponse createPrescription(CreatePrescriptionRequest request) {
        validateDates(request.prescribedAt(), request.expiresAt());
        validateCustomer(request.customerId());
        validatePrescriber(request.prescriberId());
        
        Prescription prescription = new Prescription(
            request.customerId(),
            request.prescriberId(),
            request.prescribedAt(),
            request.expiresAt()
        );
        
        for (PrescriptionLineCreateRequest lineReq : request.lines()) {
            PrescriptionLine line = new PrescriptionLine(
                prescription.getId(),
                lineReq.productId(),
                lineReq.quantity(),
                lineReq.instructions()
            );
            prescription.addLine(line);
        }
        
        Prescription saved = prescriptionRepository.save(prescription);
        log.info("prescription.created prescriptionId={} outcome=pending", saved.getId());
        return prescriptionMapper.toResponse(saved);
    }

    public PrescriptionResponse getPrescription(String prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
            .orElseThrow(() -> new PrescriptionNotFoundException(prescriptionId));
        return prescriptionMapper.toResponse(prescription);
    }

    public PageResponse<PrescriptionResponse> listByCustomerId(String customerId, Pageable pageable) {
        Page<Prescription> page = prescriptionRepository.findByCustomerId(customerId, pageable);
        return mapPageResponse(page);
    }

    public PageResponse<PrescriptionResponse> listByStatus(PrescriptionStatus status, Pageable pageable) {
        Page<Prescription> page = prescriptionRepository.findByStatus(status, pageable);
        return mapPageResponse(page);
    }

    @RecordBusinessMetric(BusinessMetric.PRESCRIPTION_VERIFICATION)
    public PrescriptionResponse activatePrescription(String prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
            .orElseThrow(() -> new PrescriptionNotFoundException(prescriptionId));
        
        if (!prescription.isPending()) {
            log.debug("prescription.verification.completed prescriptionId={} outcome=rejected reason=invalid_state",
                prescription.getId());
            throw new InvalidPrescriptionStateException(
                "Can only activate PENDING prescriptions, current status: " + prescription.getStatus()
            );
        }
        
        prescription.activate();
        Prescription saved = prescriptionRepository.save(prescription);
        log.info("prescription.verification.completed prescriptionId={} outcome=activated", saved.getId());
        return prescriptionMapper.toResponse(saved);
    }

    public PrescriptionResponse fillPrescriptionLine(String prescriptionId, String lineId, java.math.BigDecimal dispensedQuantity) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
            .orElseThrow(() -> new PrescriptionNotFoundException(prescriptionId));
        
        if (!prescription.isActive()) {
            throw new InvalidPrescriptionStateException(
                "Can only fill ACTIVE prescriptions, current status: " + prescription.getStatus()
            );
        }
        
        PrescriptionLine line = prescriptionLineRepository.findById(lineId)
            .orElseThrow(() -> new PrescriptionLineNotFoundException(lineId));
        
        if (!line.getPrescriptionId().equals(prescriptionId)) {
            throw new InvalidPrescriptionStateException("Line does not belong to prescription");
        }
        
        line.fill(dispensedQuantity);
        prescriptionLineRepository.save(line);
        
        prescription.checkAndUpdateStatus();
        Prescription saved = prescriptionRepository.save(prescription);
        log.info("Filled prescription line: {} for prescription: {}", lineId, prescriptionId);
        return prescriptionMapper.toResponse(saved);
    }

    public void expirePrescriptions() {
        Instant now = Instant.now();
        List<Prescription> expiredPrescriptions = prescriptionRepository.findExpiredPrescriptions(
            PrescriptionStatus.ACTIVE, now
        );
        
        for (Prescription prescription : expiredPrescriptions) {
            prescription.expire();
            prescriptionRepository.save(prescription);
        }
        if (!expiredPrescriptions.isEmpty()) {
            log.info("prescription.expiration.completed count={}", expiredPrescriptions.size());
        }
    }

    @Transactional(readOnly = true)
    private void validateCustomer(String customerId) {
        // In a real implementation, this would call customer-service
        // For now, we just validate the UUID format
        if (!isValidUUID(customerId)) {
            throw new CustomerValidationException("Invalid customer ID format");
        }
    }

    @Transactional(readOnly = true)
    private void validatePrescriber(String prescriberId) {
        // In a real implementation, this would call customer-service with role=PRESCRIBER
        // For now, we just validate the UUID format
        if (!isValidUUID(prescriberId)) {
            throw new PrescriberValidationException("Invalid prescriber ID format");
        }
    }

    private void validateDates(Instant prescribedAt, Instant expiresAt) {
        Instant now = Instant.now();
        
        if (prescribedAt.isBefore(now)) {
            throw new InvalidPrescriptionStateException("Prescribed at must be today or future");
        }
        
        if (!expiresAt.isAfter(prescribedAt)) {
            throw new InvalidPrescriptionStateException("Expires at must be after prescribed at");
        }
    }

    private boolean isValidUUID(String value) {
        try {
            java.util.UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private PageResponse<PrescriptionResponse> mapPageResponse(Page<Prescription> page) {
        return new PageResponse<>(
            page.getContent().stream()
                .map(prescriptionMapper::toResponse)
                .toList(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages()
        );
    }
}
