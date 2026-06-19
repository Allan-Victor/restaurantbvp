package com.allan.bvp.restaurant.domain.repository;

import com.allan.bvp.restaurant.domain.model.StockItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for managing {@link StockItem} persistence.
 */
public interface StockItemRepository extends JpaRepository<StockItem, UUID> {
    /**
     * Finds a stock item by its associated ingredient ID.
     *
     * @param ingredientId the ID of the ingredient
     * @return an Optional containing the stock item if found
     */
    Optional<StockItem> findByIngredientId(UUID ingredientId);

    /**
     * Finds a stock item for a specific venue and ingredient.
     *
     * @param venueId the ID of the venue
     * @param ingredientId the ID of the ingredient
     * @return an Optional containing the stock item if found
     */
    Optional<StockItem> findByVenueIdAndIngredientId(String venueId, UUID ingredientId);
}
