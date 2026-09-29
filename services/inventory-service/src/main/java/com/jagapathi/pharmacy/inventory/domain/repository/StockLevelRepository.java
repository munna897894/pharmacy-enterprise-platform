package com.jagapathi.pharmacy.inventory.domain.repository;

import com.jagapathi.pharmacy.inventory.domain.model.StockLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockLevelRepository extends JpaRepository<StockLevel, String> {
    Optional<StockLevel> findByPharmacyIdAndProductId(String pharmacyId, String productId);
    
    List<StockLevel> findByPharmacyId(String pharmacyId);
    
    @Query("SELECT sl FROM StockLevel sl WHERE sl.pharmacyId = :pharmacyId AND sl.status = :status")
    List<StockLevel> findByPharmacyIdAndStatus(@Param("pharmacyId") String pharmacyId, @Param("status") String status);
}
