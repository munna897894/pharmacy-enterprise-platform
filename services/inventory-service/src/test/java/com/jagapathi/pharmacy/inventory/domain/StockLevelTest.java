package com.jagapathi.pharmacy.inventory.domain;

import com.jagapathi.pharmacy.inventory.domain.model.StockLevel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

class StockLevelTest {

    @Test
    void testCreateStockLevel() {
        StockLevel stockLevel = new StockLevel(
                "p1", "m1", BigDecimal.valueOf(100),
                BigDecimal.valueOf(50), BigDecimal.valueOf(100)
        );

        assertThat(stockLevel.getId()).isNotNull();
        assertThat(stockLevel.getPharmacyId()).isEqualTo("p1");
        assertThat(stockLevel.getProductId()).isEqualTo("m1");
        assertThat(stockLevel.getQuantityOnHand()).isEqualTo(BigDecimal.valueOf(100));
        assertThat(stockLevel.getStatus()).isEqualTo("IN_STOCK");
        assertThat(stockLevel.getVersion()).isNull();
    }

    @Test
    void testAddStock() {
        StockLevel stockLevel = new StockLevel(
                "p1", "m1", BigDecimal.valueOf(100),
                BigDecimal.valueOf(50), BigDecimal.valueOf(100)
        );

        stockLevel.addStock(BigDecimal.valueOf(50));

        assertThat(stockLevel.getQuantityOnHand()).isEqualTo(BigDecimal.valueOf(150));
        assertThat(stockLevel.getStatus()).isEqualTo("IN_STOCK");
    }

    @Test
    void testRemoveStock() {
        StockLevel stockLevel = new StockLevel(
                "p1", "m1", BigDecimal.valueOf(100),
                BigDecimal.valueOf(50), BigDecimal.valueOf(100)
        );

        stockLevel.removeStock(BigDecimal.valueOf(60));

        assertThat(stockLevel.getQuantityOnHand()).isEqualTo(BigDecimal.valueOf(40));
        assertThat(stockLevel.getStatus()).isEqualTo("LOW_STOCK");
    }

    @Test
    void testRemoveStockToZero() {
        StockLevel stockLevel = new StockLevel(
                "p1", "m1", BigDecimal.valueOf(100),
                BigDecimal.valueOf(50), BigDecimal.valueOf(100)
        );

        stockLevel.removeStock(BigDecimal.valueOf(100));

        assertThat(stockLevel.getQuantityOnHand()).isEqualTo(BigDecimal.ZERO);
        assertThat(stockLevel.getStatus()).isEqualTo("OUT_OF_STOCK");
    }

    @Test
    void testAddNegativeQuantityThrows() {
        StockLevel stockLevel = new StockLevel(
                "p1", "m1", BigDecimal.valueOf(100),
                BigDecimal.valueOf(50), BigDecimal.valueOf(100)
        );

        assertThatThrownBy(() -> stockLevel.addStock(BigDecimal.valueOf(-10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    void testRemoveInsufficientStockThrows() {
        StockLevel stockLevel = new StockLevel(
                "p1", "m1", BigDecimal.valueOf(100),
                BigDecimal.valueOf(50), BigDecimal.valueOf(100)
        );

        assertThatThrownBy(() -> stockLevel.removeStock(BigDecimal.valueOf(150)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient stock");
    }
}
