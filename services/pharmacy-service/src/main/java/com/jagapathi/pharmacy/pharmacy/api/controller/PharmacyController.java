package com.jagapathi.pharmacy.pharmacy.api.controller;

import com.jagapathi.pharmacy.pharmacy.api.request.CreatePharmacyAddressRequest;
import com.jagapathi.pharmacy.pharmacy.api.request.CreatePharmacyRequest;
import com.jagapathi.pharmacy.pharmacy.api.request.UpdatePharmacyRequest;
import com.jagapathi.pharmacy.pharmacy.api.request.UpdatePharmacyStatusRequest;
import com.jagapathi.pharmacy.pharmacy.api.response.PharmacyAddressResponse;
import com.jagapathi.pharmacy.pharmacy.api.response.PharmacyResponse;
import com.jagapathi.pharmacy.pharmacy.application.service.PharmacyService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/pharmacies")
public class PharmacyController {

    private final PharmacyService pharmacyService;

    public PharmacyController(PharmacyService pharmacyService) {
        this.pharmacyService = pharmacyService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PharmacyResponse>> searchPharmacies(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String postalCode) {
        List<PharmacyResponse> pharmacies = pharmacyService.searchPharmacies(status, postalCode);
        return ResponseEntity.ok(pharmacies);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PharmacyResponse> getPharmacy(@PathVariable String id) {
        PharmacyResponse pharmacy = pharmacyService.getPharmacy(id);
        return ResponseEntity.ok(pharmacy);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PharmacyResponse> createPharmacy(
            @Valid @RequestBody CreatePharmacyRequest request,
            Authentication authentication) {
        List<String> roles = extractRoles(authentication);
        PharmacyResponse pharmacy = pharmacyService.createPharmacy(request, roles);
        
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(pharmacy.id())
                .toUri();
        
        return ResponseEntity.created(location).body(pharmacy);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('STORE_MANAGER', 'ADMIN')")
    public ResponseEntity<PharmacyResponse> updatePharmacy(
            @PathVariable String id,
            @Valid @RequestBody UpdatePharmacyRequest request,
            Authentication authentication) {
        List<String> roles = extractRoles(authentication);
        PharmacyResponse pharmacy = pharmacyService.updatePharmacy(id, request, roles);
        return ResponseEntity.ok(pharmacy);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('STORE_MANAGER', 'ADMIN')")
    public ResponseEntity<PharmacyResponse> updatePharmacyStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdatePharmacyStatusRequest request,
            Authentication authentication) {
        List<String> roles = extractRoles(authentication);
        PharmacyResponse pharmacy = pharmacyService.updatePharmacyStatus(id, request.status(), roles);
        return ResponseEntity.ok(pharmacy);
    }

    @PostMapping("/{id}/addresses")
    @PreAuthorize("hasAnyRole('STORE_MANAGER', 'ADMIN')")
    public ResponseEntity<PharmacyAddressResponse> addPharmacyAddress(
            @PathVariable String id,
            @Valid @RequestBody CreatePharmacyAddressRequest request) {
        PharmacyAddressResponse address = pharmacyService.addPharmacyAddress(id, request);
        
        URI location = ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/api/v1/pharmacies/{id}/addresses/{addressId}")
                .buildAndExpand(id, address.id())
                .toUri();
        
        return ResponseEntity.created(location).body(address);
    }

    private List<String> extractRoles(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return List.of();
        }
        return authentication.getAuthorities().stream()
                .map(auth -> auth.getAuthority())
                .toList();
    }
}
