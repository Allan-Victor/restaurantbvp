package com.allan.bvp.restaurant.domain.service;

import com.allan.bvp.restaurant.domain.model.Ingredient;
import com.allan.bvp.restaurant.domain.repository.IngredientRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link IngredientService}.
 */
@ExtendWith(MockitoExtension.class)
public class IngredientServiceTest {

    @Mock
    private IngredientRepository ingredientRepository;

    @Mock
    private RecipeService recipeService;

    @InjectMocks
    private IngredientService ingredientService;

    @Test
    void updateIngredientCost_ShouldUpdateAndTriggerRecalculation() {
        // Arrange
        UUID ingredientId = UUID.randomUUID();
        Ingredient ingredient = Ingredient.builder().id(ingredientId).unitCostMinor(100L).build();
        Long newCost = 150L;

        when(ingredientRepository.findById(ingredientId)).thenReturn(Optional.of(ingredient));
        when(ingredientRepository.save(ingredient)).thenReturn(ingredient);

        // Act
        Ingredient result = ingredientService.updateIngredientCost(ingredientId, newCost);

        // Assert
        assertEquals(newCost, result.getUnitCostMinor());
        verify(recipeService).recalculateAllRecipesUsingIngredient(ingredientId);
    }
}
