package com.jagapathi.pharmacy.prescription.api.controller;

import com.jagapathi.pharmacy.prescription.api.request.CreatePrescriptionRequest;
import com.jagapathi.pharmacy.prescription.api.request.FillPrescriptionLineRequest;
import com.jagapathi.pharmacy.prescription.api.response.PrescriptionResponse;
import com.jagapathi.pharmacy.prescription.api.response.PageResponse;
import com.jagapathi.pharmacy.prescription.application.service.PrescriptionService;
import com.jagapathi.pharmacy.prescription.domain.model.PrescriptionStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/prescriptions")
@Tag(name = "Prescriptions", description = "Prescription management")
public class PrescriptionController {
    
    private final PrescriptionService prescriptionService;
    
    public PrescriptionController(PrescriptionService prescriptionService) {
        this.prescriptionService = prescriptionService;
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get prescription by ID")
    public ResponseEntity<PrescriptionResponse> getPrescription(@PathVariable String id) {
        PrescriptionResponse response = prescriptionService.getPrescription(id);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List prescriptions by status")
    public ResponseEntity<PageResponse<PrescriptionResponse>> listByStatus(
            @RequestParam(required = false) PrescriptionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        if (size > 100) {
            size = 100;
        }
        Pageable pageable = PageRequest.of(page, size);
        
        PageResponse<PrescriptionResponse> response = prescriptionService.listByStatus(status, pageable);
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/customers/{customerId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List prescriptions for a customer")
    public ResponseEntity<PageResponse<PrescriptionResponse>> listByCustomer(
            @PathVariable String customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        if (size > 100) {
            size = 100;
        }
        Pageable pageable = PageRequest.of(page, size);
        
        PageResponse<PrescriptionResponse> response = prescriptionService.listByCustomerId(customerId, pageable);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping
    @PreAuthorize("hasAnyRole('STORE_MANAGER', 'ADMIN', 'PHARMACIST')")
    @Operation(summary = "Create prescription")
    public ResponseEntity<PrescriptionResponse> createPrescription(
            @Valid @RequestBody CreatePrescriptionRequest request) {
        PrescriptionResponse response = prescriptionService.createPrescription(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'ADMIN')")
    @Operation(summary = "Activate prescription")
    public ResponseEntity<PrescriptionResponse> activatePrescription(@PathVariable String id) {
        PrescriptionResponse response = prescriptionService.activatePrescription(id);
        return ResponseEntity.ok(response);
    }
    
    @PutMapping("/{id}/lines/{lineId}/fill")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'ADMIN')")
    @Operation(summary = "Fill prescription line")
    public ResponseEntity<PrescriptionResponse> fillPrescriptionLine(
            @PathVariable String id,
            @PathVariable String lineId,
            @Valid @RequestBody FillPrescriptionLineRequest request) {
        PrescriptionResponse response = prescriptionService.fillPrescriptionLine(id, lineId, request.dispensedQuantity());
        return ResponseEntity.ok(response);
    }
}
