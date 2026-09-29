package com.jagapathi.pharmacy.product.domain.repository;

import com.jagapathi.pharmacy.product.domain.model.Medication;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MedicationRepository extends JpaRepository<Medication, String> {

    Optional<Medication> findByNdcCode(String ndcCode);

    @Query("SELECT m FROM Medication m WHERE " +
           "(:q IS NULL OR m.name ILIKE CONCAT('%', :q, '%') OR m.genericName ILIKE CONCAT('%', :q, '%') OR m.ndcCode ILIKE CONCAT('%', :q, '%')) AND " +
           "(:manufacturer IS NULL OR m.manufacturer ILIKE CONCAT('%', :manufacturer, '%')) AND " +
           "(:dosageForm IS NULL OR m.dosageForm ILIKE CONCAT('%', :dosageForm, '%')) AND " +
           "(:active IS NULL OR m.active = :active)")
    Page<Medication> searchMedications(
            @Param("q") String query,
            @Param("manufacturer") String manufacturer,
            @Param("dosageForm") String dosageForm,
            @Param("active") Boolean active,
            Pageable pageable);
}
