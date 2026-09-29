package com.jagapathi.pharmacy.inventory.application.service;

import com.jagapathi.pharmacy.inventory.api.request.AdjustStockRequest;
import com.jagapathi.pharmacy.inventory.api.request.CreateStockLevelRequest;
import com.jagapathi.pharmacy.inventory.api.response.StockLevelResponse;
import com.jagapathi.pharmacy.inventory.domain.exception.InventoryAuthorizationException;
import com.jagapathi.pharmacy.inventory.domain.exception.StockLevelNotFoundException;
import com.jagapathi.pharmacy.inventory.domain.model.StockLevel;
import com.jagapathi.pharmacy.inventory.domain.repository.StockAdjustmentRepository;
import com.jagapathi.pharmacy.inventory.domain.repository.StockLevelRepository;
import com.jagapathi.pharmacy.inventory.domain.repository.InventoryReservationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private StockLevelRepository stockLevelRepository;

    @Mock
    private StockAdjustmentRepository adjustmentRepository;

    @Mock
    private InventoryReservationRepository reservationRepository;

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(stockLevelRepository, adjustmentRepository, reservationRepository);
    }

    @Test
    void testGetStockLevel_Success() {
        StockLevel stockLevel = new StockLevel("p1", "m1", BigDecimal.valueOf(100),
                BigDecimal.valueOf(50), BigDecimal.valueOf(100));
        when(stockLevelRepository.findById("s1")).thenReturn(Optional.of(stockLevel));

        StockLevelResponse response = inventoryService.getStockLevel("s1");

        assertThat(response).isNotNull();
        assertThat(response.quantityOnHand()).isEqualTo(BigDecimal.valueOf(100));
    }

    @Test
    void testGetStockLevel_NotFound() {
        when(stockLevelRepository.findById("nonexistent")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.getStockLevel("nonexistent"))
                .isInstanceOf(StockLevelNotFoundException.class);
    }

    @Test
    void testGetPharmacyProductStock_Success() {
        StockLevel stockLevel = new StockLevel("p1", "m1", BigDecimal.valueOf(100),
                BigDecimal.valueOf(50), BigDecimal.valueOf(100));
        when(stockLevelRepository.findByPharmacyIdAndProductId("p1", "m1"))
                .thenReturn(Optional.of(stockLevel));

        StockLevelResponse response = inventoryService.getPharmacyProductStock("p1", "m1");

        assertThat(response).isNotNull();
        assertThat(response.pharmacyId()).isEqualTo("p1");
        assertThat(response.productId()).isEqualTo("m1");
    }

    @Test
    void testCreateStockLevel_Success() {
        CreateStockLevelRequest request = new CreateStockLevelRequest(
                "p1", "m1", BigDecimal.valueOf(100),
                BigDecimal.valueOf(50), BigDecimal.valueOf(100)
        );
        StockLevel stockLevel = new StockLevel(request.pharmacyId(), request.productId(),
                request.quantityOnHand(), request.reorderLevel(), request.reorderQuantity());
        when(stockLevelRepository.save(any(StockLevel.class))).thenReturn(stockLevel);

        StockLevelResponse response = inventoryService.createStockLevel(request, List.of("ROLE_ADMIN"));

        assertThat(response).isNotNull();
        assertThat(response.quantityOnHand()).isEqualTo(BigDecimal.valueOf(100));
    }

    @Test
    void testCreateStockLevel_UnauthorizedNonStaff() {
        CreateStockLevelRequest request = new CreateStockLevelRequest(
                "p1", "m1", BigDecimal.valueOf(100),
                BigDecimal.valueOf(50), BigDecimal.valueOf(100)
        );

        assertThatThrownBy(() -> inventoryService.createStockLevel(request, List.of("ROLE_CUSTOMER")))
                .isInstanceOf(InventoryAuthorizationException.class);
    }

    @Test
    void testAdjustStock_AddStock() {
        StockLevel stockLevel = new StockLevel("p1", "m1", BigDecimal.valueOf(100),
                BigDecimal.valueOf(50), BigDecimal.valueOf(100));
        AdjustStockRequest request = new AdjustStockRequest(
                BigDecimal.valueOf(50), "PURCHASE", "Restocking"
        );
        when(stockLevelRepository.findById("s1")).thenReturn(Optional.of(stockLevel));
        when(stockLevelRepository.save(any(StockLevel.class))).thenReturn(stockLevel);
        when(adjustmentRepository.save(any())).thenReturn(null);

        StockLevelResponse response = inventoryService.adjustStock("s1", request, "u1", List.of("ROLE_PHARMACIST"));

        assertThat(response).isNotNull();
        assertThat(response.quantityOnHand()).isEqualTo(BigDecimal.valueOf(150));
    }

    @Test
    void testAdjustStock_RemoveStock() {
        StockLevel stockLevel = new StockLevel("p1", "m1", BigDecimal.valueOf(100),
                BigDecimal.valueOf(50), BigDecimal.valueOf(100));
        AdjustStockRequest request = new AdjustStockRequest(
                BigDecimal.valueOf(30), "SALE", "Customer purchase"
        );
        when(stockLevelRepository.findById("s1")).thenReturn(Optional.of(stockLevel));
        when(stockLevelRepository.save(any(StockLevel.class))).thenReturn(stockLevel);
        when(adjustmentRepository.save(any())).thenReturn(null);

        StockLevelResponse response = inventoryService.adjustStock("s1", request, "u1", List.of("ROLE_ADMIN"));

        assertThat(response).isNotNull();
        assertThat(response.quantityOnHand()).isEqualTo(BigDecimal.valueOf(70));
    }

    @Test
    void testAdjustStock_UnauthorizedCustomer() {
        AdjustStockRequest request = new AdjustStockRequest(
                BigDecimal.valueOf(50), "PURCHASE", "Test"
        );

        assertThatThrownBy(() -> inventoryService.adjustStock("s1", request, "u1", List.of("ROLE_CUSTOMER")))
                .isInstanceOf(InventoryAuthorizationException.class);
    }
}
