package com.allan.bvp.restaurant.domain.repository;

import com.allan.bvp.restaurant.domain.model.RecipeComponent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

/**
 * Repository interface for managing {@link RecipeComponent} persistence.
 */
public interface RecipeComponentRepository extends JpaRepository<RecipeComponent, UUID> {
    /**
     * Finds all recipe components that use a specific ingredient.
     *
     * @param ingredientId the ID of the ingredient
     * @return a list of matching recipe components
     */
    List<RecipeComponent> findByIngredientId(UUID ingredientId);

    /**
     * Finds all recipe components that use a specific sub-recipe.
     *
     * @param subRecipeId the ID of the sub-recipe
     * @return a list of matching recipe components
     */
    List<RecipeComponent> findBySubRecipeId(UUID subRecipeId);
}
