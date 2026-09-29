package com.jagapathi.pharmacy.prescription.domain.repository;

import com.jagapathi.pharmacy.prescription.domain.model.PrescriptionLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PrescriptionLineRepository extends JpaRepository<PrescriptionLine, String> {
    List<PrescriptionLine> findByPrescriptionId(String prescriptionId);
    
    List<PrescriptionLine> findByProductId(String productId);
}
