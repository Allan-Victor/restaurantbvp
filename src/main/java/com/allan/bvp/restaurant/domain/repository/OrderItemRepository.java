package com.allan.bvp.restaurant.domain.repository;

import com.allan.bvp.restaurant.domain.model.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

/**
 * Repository interface for managing {@link OrderItem} persistence.
 */
public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {
}
