package com.allan.bvp.restaurant.domain.repository;

import com.allan.bvp.restaurant.domain.model.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for managing {@link StockMovement} persistence.
 */
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {
    /**
     * Finds all movements for a specific stock item, ordered by creation time.
     *
     * @param stockItemId the ID of the stock item
     * @return a list of stock movements
     */
    List<StockMovement> findByStockItemIdOrderByCreatedAtAsc(UUID stockItemId);

    /**
     * Checks if a movement already exists for a specific venue, stock item, and idempotency key.
     *
     * @param venueId the ID of the venue
     * @param stockItemId the ID of the stock item
     * @param idempotencyKey the unique idempotency key
     * @return true if the movement exists, false otherwise
     */
    boolean existsByVenueIdAndStockItemIdAndIdempotencyKey(String venueId, UUID stockItemId, String idempotencyKey);
}
