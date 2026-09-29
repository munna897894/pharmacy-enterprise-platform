package com.jagapathi.pharmacy.inventory.api.controller;

import com.jagapathi.pharmacy.inventory.api.request.AdjustStockRequest;
import com.jagapathi.pharmacy.inventory.api.request.CreateStockLevelRequest;
import com.jagapathi.pharmacy.inventory.api.response.InventoryAvailabilityResponse;
import com.jagapathi.pharmacy.inventory.api.response.InventoryReservationResponse;
import com.jagapathi.pharmacy.inventory.api.response.StockAdjustmentResponse;
import com.jagapathi.pharmacy.inventory.api.response.StockLevelResponse;
import com.jagapathi.pharmacy.inventory.application.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/stock-levels/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StockLevelResponse> getStockLevel(@PathVariable String id) {
        StockLevelResponse response = inventoryService.getStockLevel(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/pharmacies/{pharmacyId}/products/{productId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<StockLevelResponse> getPharmacyProductStock(
            @PathVariable String pharmacyId,
            @PathVariable String productId) {
        StockLevelResponse response = inventoryService.getPharmacyProductStock(pharmacyId, productId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/pharmacies/{pharmacyId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<StockLevelResponse>> getPharmacyInventory(@PathVariable String pharmacyId) {
        List<StockLevelResponse> response = inventoryService.getPharmacyInventory(pharmacyId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/pharmacies/{pharmacyId}/low-stock")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<StockLevelResponse>> getLowStockItems(@PathVariable String pharmacyId) {
        List<StockLevelResponse> response = inventoryService.getLowStockItems(pharmacyId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/stock-levels")
    @PreAuthorize("hasAnyRole('STORE_MANAGER', 'ADMIN')")
    public ResponseEntity<StockLevelResponse> createStockLevel(
            @Valid @RequestBody CreateStockLevelRequest request,
            Authentication authentication) {
        List<String> roles = extractRoles(authentication);
        StockLevelResponse response = inventoryService.createStockLevel(request, roles);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @PostMapping("/stock-levels/{stockLevelId}/adjust")
    @PreAuthorize("hasAnyRole('PHARMACIST', 'STORE_MANAGER', 'ADMIN')")
    public ResponseEntity<StockLevelResponse> adjustStock(
            @PathVariable String stockLevelId,
            @Valid @RequestBody AdjustStockRequest request,
            Authentication authentication) {
        String adjustedBy = extractUserId(authentication);
        List<String> roles = extractRoles(authentication);
        StockLevelResponse response = inventoryService.adjustStock(stockLevelId, request, adjustedBy, roles);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stock-levels/{stockLevelId}/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<StockAdjustmentResponse>> getAdjustmentHistory(@PathVariable String stockLevelId) {
        List<StockAdjustmentResponse> response = inventoryService.getAdjustmentHistory(stockLevelId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/availability")
    @PreAuthorize("permitAll()")
    public ResponseEntity<InventoryAvailabilityResponse> getAvailability(
            @RequestParam String pharmacyId,
            @RequestParam String medicationId,
            @RequestParam(required = false) BigDecimal quantity) {
        StockLevelResponse stockLevel = inventoryService.getPharmacyProductStock(pharmacyId, medicationId);
        boolean available = stockLevel.quantityOnHand() != null && stockLevel.quantityOnHand().compareTo(BigDecimal.ZERO) > 0;
        if (quantity != null && quantity.compareTo(BigDecimal.ZERO) > 0) {
            available = stockLevel.quantityOnHand() != null && stockLevel.quantityOnHand().compareTo(quantity) >= 0;
        }
        String reason = available ? "available" : "insufficient stock";
        return ResponseEntity.ok(new InventoryAvailabilityResponse(available, reason));
    }

    @GetMapping("/reservations/{orderId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InventoryReservationResponse> getReservation(@PathVariable String orderId) {
        return ResponseEntity.ok(inventoryService.getReservationByOrderId(orderId));
    }

    private String extractUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return "unknown";
        }
        return authentication.getName();
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
