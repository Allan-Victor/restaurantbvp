package com.allan.bvp.restaurant.domain.service;

import com.allan.bvp.restaurant.domain.model.Ingredient;
import com.allan.bvp.restaurant.domain.repository.IngredientRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for managing ingredients and their costs.
 */
@Service
@RequiredArgsConstructor
public class IngredientService {

    private final IngredientRepository ingredientRepository;
    private final RecipeService recipeService;

    /**
     * Updates the cost of an ingredient and triggers a recalculation of all
     * recipes that use this ingredient.
     *
     * @param ingredientId the ID of the ingredient
     * @param newCostMinor the new unit cost in minor units
     * @return the updated ingredient
     */
    @Transactional
    public Ingredient updateIngredientCost(UUID ingredientId, Long newCostMinor) {
        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new IllegalArgumentException("Ingredient not found"));
        
        ingredient.setUnitCostMinor(newCostMinor);
        Ingredient saved = ingredientRepository.save(ingredient);
        
        recipeService.recalculateAllRecipesUsingIngredient(ingredientId);
        
        return saved;
    }
}
