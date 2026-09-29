package com.jagapathi.pharmacy.pharmacy.domain.repository;

import com.jagapathi.pharmacy.pharmacy.domain.model.Pharmacy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PharmacyRepository extends JpaRepository<Pharmacy, String> {
    @Query("SELECT p FROM Pharmacy p WHERE " +
           "(:status IS NULL OR p.status = :status) AND " +
           "(:postalCode IS NULL OR EXISTS (SELECT 1 FROM PharmacyAddress pa WHERE pa.pharmacyId = p.id AND pa.postalCode LIKE %:postalCode%)) " +
           "ORDER BY p.name")
    List<Pharmacy> searchPharmacies(@Param("status") String status, @Param("postalCode") String postalCode);
}
