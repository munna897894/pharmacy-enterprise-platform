package com.jagapathi.pharmacy.order.infrastructure;

import com.jagapathi.pharmacy.order.domain.OrderItem;
import com.jagapathi.pharmacy.order.domain.OrderItemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
    List<OrderItem> findByOrderId(UUID orderId);
    List<OrderItem> findByStatus(OrderItemStatus status);
    List<OrderItem> findByMedicationId(UUID medicationId);
}
