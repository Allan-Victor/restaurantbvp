package com.allan.bvp.restaurant.domain.repository;

import com.allan.bvp.restaurant.domain.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

/**
 * Repository interface for managing {@link Order} persistence.
 */
public interface OrderRepository extends JpaRepository<Order, UUID> {
}
