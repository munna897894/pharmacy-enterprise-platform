package com.jagapathi.pharmacy.inventory.application.service;

import com.jagapathi.pharmacy.inventory.api.request.AdjustStockRequest;
import com.jagapathi.pharmacy.inventory.api.request.CreateStockLevelRequest;
import com.jagapathi.pharmacy.inventory.api.response.InventoryReservationResponse;
import com.jagapathi.pharmacy.inventory.api.response.StockAdjustmentResponse;
import com.jagapathi.pharmacy.inventory.api.response.StockLevelResponse;
import com.jagapathi.pharmacy.inventory.domain.exception.InventoryAuthorizationException;
import com.jagapathi.pharmacy.inventory.domain.exception.ReservationNotFoundException;
import com.jagapathi.pharmacy.inventory.domain.exception.StockLevelAlreadyExistsException;
import com.jagapathi.pharmacy.inventory.domain.exception.StockLevelNotFoundException;
import com.jagapathi.pharmacy.inventory.domain.model.StockAdjustment;
import com.jagapathi.pharmacy.inventory.domain.model.StockLevel;
import com.jagapathi.pharmacy.inventory.domain.repository.InventoryReservationRepository;
import com.jagapathi.pharmacy.inventory.domain.repository.StockAdjustmentRepository;
import com.jagapathi.pharmacy.inventory.domain.repository.StockLevelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class InventoryService {

    private final StockLevelRepository stockLevelRepository;
    private final StockAdjustmentRepository adjustmentRepository;
    private final InventoryReservationRepository reservationRepository;

    public InventoryService(StockLevelRepository stockLevelRepository,
                           StockAdjustmentRepository adjustmentRepository,
                           InventoryReservationRepository reservationRepository) {
        this.stockLevelRepository = stockLevelRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional(readOnly = true)
    public StockLevelResponse getStockLevel(String stockLevelId) {
        StockLevel stockLevel = stockLevelRepository.findById(stockLevelId)
                .orElseThrow(() -> new StockLevelNotFoundException(stockLevelId));
        return StockLevelResponse.from(stockLevel);
    }

    @Transactional(readOnly = true)
    public StockLevelResponse getPharmacyProductStock(String pharmacyId, String productId) {
        StockLevel stockLevel = stockLevelRepository.findByPharmacyIdAndProductId(pharmacyId, productId)
                .orElseThrow(() -> new StockLevelNotFoundException("No stock for pharmacy " + pharmacyId + " product " + productId));
        return StockLevelResponse.from(stockLevel);
    }

    @Transactional(readOnly = true)
    public List<StockLevelResponse> getPharmacyInventory(String pharmacyId) {
        return stockLevelRepository.findByPharmacyId(pharmacyId).stream()
                .map(StockLevelResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StockLevelResponse> getLowStockItems(String pharmacyId) {
        return stockLevelRepository.findByPharmacyIdAndStatus(pharmacyId, "LOW_STOCK").stream()
                .map(StockLevelResponse::from)
                .toList();
    }

    public StockLevelResponse createStockLevel(CreateStockLevelRequest request, List<String> roles) {
        if (!hasRole(roles, "STORE_MANAGER", "ADMIN")) {
            throw new InventoryAuthorizationException("User");
        }

        stockLevelRepository.findByPharmacyIdAndProductId(request.pharmacyId(), request.productId())
                .ifPresent(existing -> {
                    throw new StockLevelAlreadyExistsException(request.pharmacyId(), request.productId());
                });

        StockLevel stockLevel = new StockLevel(
                request.pharmacyId(),
                request.productId(),
                request.quantityOnHand(),
                request.reorderLevel(),
                request.reorderQuantity()
        );

        StockLevel saved = stockLevelRepository.save(stockLevel);
        return StockLevelResponse.from(saved);
    }

    public StockLevelResponse adjustStock(String stockLevelId, AdjustStockRequest request, String adjustedBy, List<String> roles) {
        if (!hasRole(roles, "PHARMACIST", "STORE_MANAGER", "ADMIN")) {
            throw new InventoryAuthorizationException(adjustedBy);
        }

        StockLevel stockLevel = stockLevelRepository.findById(stockLevelId)
                .orElseThrow(() -> new StockLevelNotFoundException(stockLevelId));

        boolean isAddition = isAdditionType(request.adjustmentType());
        if (isAddition) {
            stockLevel.addStock(request.quantity());
        } else {
            stockLevel.removeStock(request.quantity());
        }

        StockLevel updated = stockLevelRepository.save(stockLevel);

        StockAdjustment adjustment = new StockAdjustment(
                stockLevelId,
                request.adjustmentType(),
                request.quantity(),
                request.reason(),
                adjustedBy
        );
        adjustmentRepository.save(adjustment);

        return StockLevelResponse.from(updated);
    }

    @Transactional(readOnly = true)
    public List<StockAdjustmentResponse> getAdjustmentHistory(String stockLevelId) {
        return adjustmentRepository.findByStockLevelId(stockLevelId).stream()
                .map(StockAdjustmentResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public InventoryReservationResponse getReservationByOrderId(String orderId) {
        return reservationRepository.findByOrderId(UUID.fromString(orderId))
                .map(InventoryReservationResponse::from)
                .orElseThrow(() -> new ReservationNotFoundException(orderId));
    }

    private boolean isAdditionType(String adjustmentType) {
        return "PURCHASE".equals(adjustmentType) || "RETURN".equals(adjustmentType);
    }

    private boolean hasRole(List<String> roles, String... requiredRoles) {
        if (roles == null) return false;
        for (String required : requiredRoles) {
            if (roles.contains("ROLE_" + required)) {
                return true;
            }
        }
        return false;
    }
}
