package com.jagapathi.pharmacy.prescription.domain.repository;

import com.jagapathi.pharmacy.prescription.domain.model.Prescription;
import com.jagapathi.pharmacy.prescription.domain.model.PrescriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface PrescriptionRepository extends JpaRepository<Prescription, String> {
    Page<Prescription> findByCustomerId(String customerId, Pageable pageable);
    
    Page<Prescription> findByStatus(PrescriptionStatus status, Pageable pageable);
    
    @Query("SELECT p FROM Prescription p WHERE p.status = :status AND p.expiresAt < :now")
    List<Prescription> findExpiredPrescriptions(@Param("status") PrescriptionStatus status, @Param("now") Instant now);
}
