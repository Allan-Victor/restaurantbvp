package com.allan.bvp.restaurant.domain.repository;

import com.allan.bvp.restaurant.domain.model.MenuItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

/**
 * Repository interface for managing {@link MenuItem} persistence.
 */
public interface MenuItemRepository extends JpaRepository<MenuItem, UUID> {
}
