package com.jagapathi.pharmacy.product.api.controller;

import com.jagapathi.pharmacy.product.api.request.CreateMedicationRequest;
import com.jagapathi.pharmacy.product.api.request.UpdateMedicationRequest;
import com.jagapathi.pharmacy.product.api.request.UpdateMedicationStatusRequest;
import com.jagapathi.pharmacy.product.api.response.MedicationResponse;
import com.jagapathi.pharmacy.product.api.response.PageResponse;
import com.jagapathi.pharmacy.product.application.service.MedicationService;
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
@RequestMapping("/api/v1/medications")
@Tag(name = "Medications", description = "Medication catalog management")
public class MedicationController {

    private final MedicationService medicationService;

    public MedicationController(MedicationService medicationService) {
        this.medicationService = medicationService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Search medications")
    public ResponseEntity<PageResponse<MedicationResponse>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String manufacturer,
            @RequestParam(required = false) String dosageForm,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (size > 100) {
            size = 100;
        }
        Pageable pageable = PageRequest.of(page, size);
        PageResponse<MedicationResponse> response = medicationService.searchMedications(q, manufacturer, dosageForm, active, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get medication by ID")
    public ResponseEntity<MedicationResponse> getById(@PathVariable String id) {
        MedicationResponse response = medicationService.getMedicationById(id);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PHARMACIST', 'ADMIN')")
    @Operation(summary = "Create medication")
    public ResponseEntity<MedicationResponse> create(@Valid @RequestBody CreateMedicationRequest request) {
        MedicationResponse response = medicationService.createMedication(
                request.ndcCode(),
                request.name(),
                request.genericName(),
                request.manufacturer(),
                request.dosageForm(),
                request.strength(),
                request.unitPrice(),
                request.currency()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'ADMIN')")
    @Operation(summary = "Update medication")
    public ResponseEntity<MedicationResponse> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateMedicationRequest request) {
        MedicationResponse response = medicationService.updateMedication(
                id,
                request.name(),
                request.genericName(),
                request.manufacturer(),
                request.dosageForm(),
                request.strength(),
                request.unitPrice()
        );
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update medication status")
    public ResponseEntity<MedicationResponse> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateMedicationStatusRequest request) {
        MedicationResponse response = medicationService.updateMedicationStatus(id, request.active());
        return ResponseEntity.ok(response);
    }
}
