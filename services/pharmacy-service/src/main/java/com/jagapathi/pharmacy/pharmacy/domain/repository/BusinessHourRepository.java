package com.jagapathi.pharmacy.pharmacy.domain.repository;

import com.jagapathi.pharmacy.pharmacy.domain.model.BusinessHour;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BusinessHourRepository extends JpaRepository<BusinessHour, String> {
    List<BusinessHour> findByPharmacyId(String pharmacyId);
}
