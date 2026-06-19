package com.allan.bvp.restaurant.domain.repository;

import com.allan.bvp.restaurant.domain.model.Recipe;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

/**
 * Repository interface for managing {@link Recipe} persistence.
 */
public interface RecipeRepository extends JpaRepository<Recipe, UUID> {
}
