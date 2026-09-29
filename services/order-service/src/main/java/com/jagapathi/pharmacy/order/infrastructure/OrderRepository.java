package com.jagapathi.pharmacy.order.infrastructure;

import com.jagapathi.pharmacy.order.domain.Order;
import com.jagapathi.pharmacy.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    Page<Order> findByCustomerId(UUID customerId, Pageable pageable);
    Page<Order> findByPharmacyId(UUID pharmacyId, Pageable pageable);
    List<Order> findByStatus(OrderStatus status);
}
