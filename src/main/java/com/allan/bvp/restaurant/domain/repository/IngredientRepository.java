package com.allan.bvp.restaurant.domain.repository;

import com.allan.bvp.restaurant.domain.model.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

/**
 * Repository interface for managing {@link Ingredient} persistence.
 */
public interface IngredientRepository extends JpaRepository<Ingredient, UUID> {
}
