package com.allan.bvp.restaurant.domain.service;

import com.allan.bvp.restaurant.application.dto.FlattenedComponentDTO;
import com.allan.bvp.restaurant.domain.enums.Allergen;
import com.allan.bvp.restaurant.domain.enums.Unit;
import com.allan.bvp.restaurant.domain.model.Ingredient;
import com.allan.bvp.restaurant.domain.model.Recipe;
import com.allan.bvp.restaurant.domain.model.RecipeComponent;
import com.allan.bvp.restaurant.domain.repository.IngredientRepository;
import com.allan.bvp.restaurant.domain.repository.RecipeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RecipeService}.
 */
@ExtendWith(MockitoExtension.class)
public class RecipeServiceTest {

    @Mock
    private RecipeRepository recipeRepository;

    @Mock
    private IngredientRepository ingredientRepository;

    @InjectMocks
    private RecipeService recipeService;

    @Test
    void addIngredientComponent_ShouldAddComponentAndReturnIt() {
        // Arrange
        UUID recipeId = UUID.randomUUID();
        UUID ingredientId = UUID.randomUUID();
        Recipe recipe = Recipe.builder().id(recipeId).yield(1).components(new HashSet<>()).build();
        Ingredient ingredient = Ingredient.builder().id(ingredientId).name("Salt").unitCostMinor(100L).build();
        Long qty = 10L;
        Unit unit = Unit.GRAM;

        when(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe));
        when(ingredientRepository.findById(ingredientId)).thenReturn(Optional.of(ingredient));
        when(recipeRepository.save(any(Recipe.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Recipe result = recipeService.addIngredientComponent(recipeId, ingredientId, qty, unit);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getComponents().size());
        RecipeComponent component = result.getComponents().iterator().next();
        assertEquals(ingredient, component.getIngredient());
        assertEquals(qty, component.getRequiredQtyMinor());
        assertEquals(unit, component.getUnit());
        assertFalse(result.isIncomplete());

        verify(recipeRepository).save(recipe);
    }

    @Test
    void addSubRecipeComponent_ShouldAddComponentAndReturnIt() {
        // Arrange
        UUID recipeId = UUID.randomUUID();
        UUID subRecipeId = UUID.randomUUID();
        Recipe recipe = Recipe.builder().id(recipeId).yield(1).components(new HashSet<>()).build();
        Recipe subRecipe = Recipe.builder().id(subRecipeId).yield(1).components(new HashSet<>()).build();
        Long qty = 1L;
        Unit unit = Unit.PIECE;

        when(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe));
        when(recipeRepository.findById(subRecipeId)).thenReturn(Optional.of(subRecipe));
        when(recipeRepository.save(any(Recipe.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Recipe result = recipeService.addSubRecipeComponent(recipeId, subRecipeId, qty, unit);

        // Assert
        assertNotNull(result);
        assertEquals(1, result.getComponents().size());
        RecipeComponent component = result.getComponents().iterator().next();
        assertEquals(subRecipe, component.getSubRecipe());
        assertFalse(result.isIncomplete());

        verify(recipeRepository).save(recipe);
    }

    @Test
    void addSubRecipeComponent_ShouldRejectSelfReference() {
        // Arrange
        UUID recipeId = UUID.randomUUID();

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> 
            recipeService.addSubRecipeComponent(recipeId, recipeId, 1L, Unit.PIECE)
        );
    }

    @Test
    void addSubRecipeComponent_ShouldRejectDeepCycle() {
        // Arrange
        UUID recipeAId = UUID.randomUUID();
        UUID recipeBId = UUID.randomUUID();
        UUID recipeCId = UUID.randomUUID();

        Recipe recipeA = Recipe.builder().id(recipeAId).components(new HashSet<>()).build();
        Recipe recipeB = Recipe.builder().id(recipeBId).components(new HashSet<>()).build();
        Recipe recipeC = Recipe.builder().id(recipeCId).components(new HashSet<>()).build();

        // C -> A
        RecipeComponent compCtoA = RecipeComponent.builder().recipe(recipeC).subRecipe(recipeA).build();
        recipeC.getComponents().add(compCtoA);

        // B -> C
        RecipeComponent compBtoC = RecipeComponent.builder().recipe(recipeB).subRecipe(recipeC).build();
        recipeB.getComponents().add(compBtoC);

        // Attempting to add B to A would create A -> B -> C -> A
        when(recipeRepository.findById(recipeAId)).thenReturn(Optional.of(recipeA));
        when(recipeRepository.findById(recipeBId)).thenReturn(Optional.of(recipeB));

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> 
            recipeService.addSubRecipeComponent(recipeAId, recipeBId, 1L, Unit.PIECE)
        );
    }

    @Test
    void getFlattenedComponents_ShouldReturnAggregatedLeafIngredients() {
        // Arrange
        // Ingredient 1: 10g
        // Recipe B: uses 5g of Ingredient 1 and 2 units of Ingredient 2
        // Recipe A: uses 2 units of Recipe B and 100g of Ingredient 1
        // Total Ingredient 1 = (2 * 5) + 100 = 110
        // Total Ingredient 2 = 2 * 2 = 4

        UUID recipeAId = UUID.randomUUID();
        UUID recipeBId = UUID.randomUUID();
        Ingredient ing1 = Ingredient.builder().id(UUID.randomUUID()).name("Ing1").build();
        Ingredient ing2 = Ingredient.builder().id(UUID.randomUUID()).name("Ing2").build();

        Recipe recipeB = Recipe.builder().id(recipeBId).yield(1).components(new HashSet<>()).build();
        recipeB.getComponents().add(RecipeComponent.builder().recipe(recipeB).ingredient(ing1).requiredQtyMinor(5L).unit(Unit.GRAM).build());
        recipeB.getComponents().add(RecipeComponent.builder().recipe(recipeB).ingredient(ing2).requiredQtyMinor(2L).unit(Unit.PIECE).build());

        Recipe recipeA = Recipe.builder().id(recipeAId).yield(1).components(new HashSet<>()).build();
        recipeA.getComponents().add(RecipeComponent.builder().recipe(recipeA).subRecipe(recipeB).requiredQtyMinor(2L).unit(Unit.PIECE).build());
        recipeA.getComponents().add(RecipeComponent.builder().recipe(recipeA).ingredient(ing1).requiredQtyMinor(100L).unit(Unit.GRAM).build());

        when(recipeRepository.findById(recipeAId)).thenReturn(Optional.of(recipeA));

        // Act
        List<FlattenedComponentDTO> result = recipeService.getFlattenedComponents(recipeAId);

        // Assert
        assertEquals(2, result.size());
        FlattenedComponentDTO fIng1 = result.stream().filter(f -> f.getIngredient().equals(ing1)).findFirst().orElseThrow();
        FlattenedComponentDTO fIng2 = result.stream().filter(f -> f.getIngredient().equals(ing2)).findFirst().orElseThrow();

        assertEquals(110L, fIng1.getTotalQtyMinor());
        assertEquals(4L, fIng2.getTotalQtyMinor());
    }

    @Test
    void recalculateCostAndAllergens_ShouldUpdateRecipeFields() {
        // Arrange
        UUID recipeId = UUID.randomUUID();
        Ingredient ing1 = Ingredient.builder()
                .id(UUID.randomUUID())
                .unitCostMinor(100L)
                .allergens(Set.of(Allergen.GLUTEN))
                .build();
        ing1.setStockItem(com.allan.bvp.restaurant.domain.model.StockItem.builder().build()); // Dummy
        
        Recipe recipe = Recipe.builder()
                .id(recipeId)
                .yield(1)
                .components(new HashSet<>())
                .allergens(new HashSet<>())
                .build();

        // Recipe uses 2 units of Ing1
        RecipeComponent comp = RecipeComponent.builder()
                .recipe(recipe)
                .ingredient(ing1)
                .requiredQtyMinor(2L)
                .unit(Unit.PIECE)
                .build();
        recipe.getComponents().add(comp);

        when(recipeRepository.findById(recipeId)).thenReturn(Optional.of(recipe));
        when(recipeRepository.save(any(Recipe.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Recipe result = recipeService.recalculateCostAndAllergens(recipeId);

        // Assert
        assertEquals(200L, result.getRolledUpCostMinor());
        assertTrue(result.getAllergens().contains(Allergen.GLUTEN));
        verify(recipeRepository).save(recipe);
    }
}
