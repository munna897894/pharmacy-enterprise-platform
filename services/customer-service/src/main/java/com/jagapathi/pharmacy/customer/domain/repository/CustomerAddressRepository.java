package com.jagapathi.pharmacy.customer.domain.repository;

import com.jagapathi.pharmacy.customer.domain.model.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, String> {
    List<CustomerAddress> findByCustomerId(String customerId);
}
