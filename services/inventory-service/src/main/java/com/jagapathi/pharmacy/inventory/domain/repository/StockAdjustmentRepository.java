package com.jagapathi.pharmacy.inventory.domain.repository;

import com.jagapathi.pharmacy.inventory.domain.model.StockAdjustment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, String> {
    List<StockAdjustment> findByStockLevelId(String stockLevelId);
}
