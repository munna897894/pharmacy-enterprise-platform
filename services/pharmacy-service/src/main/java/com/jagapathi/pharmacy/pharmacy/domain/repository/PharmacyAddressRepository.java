package com.jagapathi.pharmacy.pharmacy.domain.repository;

import com.jagapathi.pharmacy.pharmacy.domain.model.PharmacyAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PharmacyAddressRepository extends JpaRepository<PharmacyAddress, String> {
    Optional<PharmacyAddress> findByPharmacyId(String pharmacyId);
}
