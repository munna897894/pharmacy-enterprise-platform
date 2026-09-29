package com.jagapathi.pharmacy.inventory.api.controller;

import com.jagapathi.pharmacy.inventory.application.service.InventoryService;
import com.jagapathi.pharmacy.inventory.api.response.StockLevelResponse;
import com.jagapathi.pharmacy.inventory.infrastructure.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;

@WebMvcTest(InventoryController.class)
@Import(TestSecurityConfig.class)
@ActiveProfiles("test")
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryService inventoryService;

    @Test
    void testCreateStockLevel_MissingPharmacyId() throws Exception {
        String request = """
                {
                    "productId": "p1",
                    "quantityOnHand": 100,
                    "reorderLevel": 50,
                    "reorderQuantity": 100
                }
                """;

        mockMvc.perform(post("/api/v1/inventory/stock-levels")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void testCreateStockLevel_InvalidQuantity() throws Exception {
        String request = """
                {
                    "pharmacyId": "p1",
                    "productId": "prod1",
                    "quantityOnHand": -10,
                    "reorderLevel": 50,
                    "reorderQuantity": 100
                }
                """;

        mockMvc.perform(post("/api/v1/inventory/stock-levels")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void testAdjustStock_InvalidAdjustmentType() throws Exception {
        String request = """
                {
                    "quantity": 10.0,
                    "adjustmentType": "INVALID",
                    "reason": "Test"
                }
                """;

        mockMvc.perform(post("/api/v1/inventory/stock-levels/s1/adjust")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    void testAdjustStock_InvalidQuantity() throws Exception {
        String request = """
                {
                    "quantity": -5.0,
                    "adjustmentType": "PURCHASE",
                    "reason": "Test"
                }
                """;

        mockMvc.perform(post("/api/v1/inventory/stock-levels/s1/adjust")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetPharmacyInventory() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/pharmacies/p1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetPharmacyProductStock() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/pharmacies/p1/products/m1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetLowStockItems() throws Exception {
        mockMvc.perform(get("/api/v1/inventory/pharmacies/p1/low-stock")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetAvailability() throws Exception {
        when(inventoryService.getPharmacyProductStock("p1", "m1"))
            .thenReturn(new StockLevelResponse("s1", "p1", "m1", BigDecimal.valueOf(10), BigDecimal.valueOf(5), BigDecimal.valueOf(10), "AVAILABLE", null, null));

        mockMvc.perform(get("/api/v1/inventory/availability")
                .param("pharmacyId", "p1")
                .param("medicationId", "m1")
                .param("quantity", "2")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }
}
