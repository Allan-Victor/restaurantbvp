package com.allan.bvp.restaurant.domain.service;

import com.allan.bvp.restaurant.application.dto.FlattenedComponentDTO;
import com.allan.bvp.restaurant.domain.enums.Allergen;
import com.allan.bvp.restaurant.domain.enums.Unit;
import com.allan.bvp.restaurant.domain.model.Ingredient;
import com.allan.bvp.restaurant.domain.model.Recipe;
import com.allan.bvp.restaurant.domain.model.RecipeComponent;
import com.allan.bvp.restaurant.domain.repository.IngredientRepository;
import com.allan.bvp.restaurant.domain.repository.RecipeComponentRepository;
import com.allan.bvp.restaurant.domain.repository.RecipeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing recipes, their components, and automatic cost/allergen recalculations.
 * This service also enforces DAG constraints to prevent circular recipe references.
 */
@Service
@RequiredArgsConstructor
public class RecipeService {

    private final RecipeRepository recipeRepository;
    private final IngredientRepository ingredientRepository;
    private final RecipeComponentRepository recipeComponentRepository;

    /**
     * Adds an ingredient component to a recipe and triggers a cost/allergen recalculation.
     *
     * @param recipeId     the ID of the recipe
     * @param ingredientId the ID of the ingredient to add
     * @param quantity     the required quantity in minor units
     * @param unit         the unit of measurement
     * @return the updated recipe
     */
    @Transactional
    public Recipe addIngredientComponent(UUID recipeId, UUID ingredientId, Long quantity, Unit unit) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new IllegalArgumentException("Recipe not found"));

        Ingredient ingredient = ingredientRepository.findById(ingredientId)
                .orElseThrow(() -> new IllegalArgumentException("Ingredient not found"));

        if (recipe.getComponents() == null) {
            recipe.setComponents(new HashSet<>());
        }

        RecipeComponent component = RecipeComponent.builder()
                .recipe(recipe)
                .ingredient(ingredient)
                .requiredQtyMinor(quantity)
                .unit(unit)
                .build();

        recipe.getComponents().add(component);
        recipe.setIncomplete(recipe.getComponents().isEmpty());

        recalculateCostAndAllergens(recipe);
        return recipeRepository.save(recipe);
    }

    /**
     * Adds a sub-recipe component to a recipe.
     * Enforces circular reference checks before adding.
     *
     * @param recipeId    the ID of the target recipe
     * @param subRecipeId the ID of the recipe to add as a component
     * @param quantity    the required quantity in minor units
     * @param unit        the unit of measurement
     * @return the updated recipe
     */
    @Transactional
    public Recipe addSubRecipeComponent(UUID recipeId, UUID subRecipeId, Long quantity, Unit unit) {
        if (recipeId.equals(subRecipeId)) {
            throw new IllegalArgumentException("Recipe cannot be a component of itself");
        }

        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new IllegalArgumentException("Recipe not found"));

        Recipe subRecipe = recipeRepository.findById(subRecipeId)
                .orElseThrow(() -> new IllegalArgumentException("Sub-recipe not found"));

        if (createsCycle(recipeId, subRecipe)) {
            throw new IllegalArgumentException("Adding this sub-recipe would create a cycle");
        }

        if (recipe.getComponents() == null) {
            recipe.setComponents(new HashSet<>());
        }

        RecipeComponent component = RecipeComponent.builder()
                .recipe(recipe)
                .subRecipe(subRecipe)
                .requiredQtyMinor(quantity)
                .unit(unit)
                .build();

        recipe.getComponents().add(component);
        recipe.setIncomplete(recipe.getComponents().isEmpty());

        recalculateCostAndAllergens(recipe);
        return recipeRepository.save(recipe);
    }

    /**
     * Recalculates the rolled-up cost and allergens for a specific recipe.
     *
     * @param recipeId the ID of the recipe to recalculate
     * @return the updated recipe
     */
    @Transactional
    public Recipe recalculateCostAndAllergens(UUID recipeId) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new IllegalArgumentException("Recipe not found"));
        recalculateCostAndAllergens(recipe);
        return recipeRepository.save(recipe);
    }

    /**
     * Recalculates all recipes that directly or indirectly use a specific ingredient.
     * Typically called when an ingredient's cost or allergens change.
     *
     * @param ingredientId the ID of the changed ingredient
     */
    @Transactional
    public void recalculateAllRecipesUsingIngredient(UUID ingredientId) {
        List<RecipeComponent> components = recipeComponentRepository.findByIngredientId(ingredientId);
        Set<Recipe> recipesToUpdate = components.stream()
                .map(RecipeComponent::getRecipe)
                .collect(Collectors.toSet());

        for (Recipe recipe : recipesToUpdate) {
            recalculateCostAndAllergens(recipe);
            recipeRepository.save(recipe);
            // Also need to update recipes that use THIS recipe as a sub-recipe
            recalculateAllRecipesUsingSubRecipe(recipe.getId());
        }
    }

    /**
     * Recalculates all recipes that use a specific recipe as a sub-recipe.
     *
     * @param subRecipeId the ID of the sub-recipe
     */
    @Transactional
    public void recalculateAllRecipesUsingSubRecipe(UUID subRecipeId) {
        List<RecipeComponent> components = recipeComponentRepository.findBySubRecipeId(subRecipeId);
        Set<Recipe> recipesToUpdate = components.stream()
                .map(RecipeComponent::getRecipe)
                .collect(Collectors.toSet());

        for (Recipe recipe : recipesToUpdate) {
            recalculateCostAndAllergens(recipe);
            recipeRepository.save(recipe);
            recalculateAllRecipesUsingSubRecipe(recipe.getId());
        }
    }

    private void recalculateCostAndAllergens(Recipe recipe) {
        long totalCost = 0;
        Set<Allergen> allAllergens = new HashSet<>();

        if (recipe.getComponents() != null) {
            for (RecipeComponent component : recipe.getComponents()) {
                double multiplier = (double) component.getRequiredQtyMinor() / recipe.getYield();
                if (component.getIngredient() != null) {
                    totalCost += (long) (component.getIngredient().getUnitCostMinor() * multiplier);
                    if (component.getIngredient().getAllergens() != null) {
                        allAllergens.addAll(component.getIngredient().getAllergens());
                    }
                } else if (component.getSubRecipe() != null) {
                    // Ensure sub-recipe is up to date
                    recalculateCostAndAllergens(component.getSubRecipe());
                    totalCost += (long) (component.getSubRecipe().getRolledUpCostMinor() * multiplier);
                    if (component.getSubRecipe().getAllergens() != null) {
                        allAllergens.addAll(component.getSubRecipe().getAllergens());
                    }
                }
            }
        }

        recipe.setRolledUpCostMinor(totalCost);
        recipe.setAllergens(allAllergens);
    }

    private boolean createsCycle(UUID recipeId, Recipe subRecipe) {
        if (subRecipe.getComponents() == null) return false;
        
        for (RecipeComponent component : subRecipe.getComponents()) {
            if (component.getSubRecipe() != null) {
                if (component.getSubRecipe().getId().equals(recipeId)) {
                    return true;
                }
                if (createsCycle(recipeId, component.getSubRecipe())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Retrieves a flattened list of all leaf ingredients required for a recipe,
     * aggregating quantities from all sub-recipe levels.
     *
     * @param recipeId the ID of the recipe to flatten
     * @return a list of flattened components
     */
    @Transactional(readOnly = true)
    public List<FlattenedComponentDTO> getFlattenedComponents(UUID recipeId) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new IllegalArgumentException("Recipe not found"));

        Map<Ingredient, Long> flattened = new HashMap<>();
        collectLeafIngredients(recipe, 1.0, flattened);

        return flattened.entrySet().stream()
                .map(e -> new FlattenedComponentDTO(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    private void collectLeafIngredients(Recipe recipe, double multiplier, Map<Ingredient, Long> flattened) {
        if (recipe.getComponents() == null) return;

        for (RecipeComponent component : recipe.getComponents()) {
            double componentQty = component.getRequiredQtyMinor() * multiplier / recipe.getYield();
            if (component.getIngredient() != null) {
                flattened.merge(component.getIngredient(), (long) componentQty, Long::sum);
            } else if (component.getSubRecipe() != null) {
                collectLeafIngredients(component.getSubRecipe(), componentQty, flattened);
            }
        }
    }

    /**
     * Finds all MenuItems that are affected by a change in a specific ingredient.
     * This traverses the recipe graph upwards to find all top-level recipes attached to menu items.
     *
     * @param ingredientId the ID of the ingredient
     * @return a set of affected MenuItem IDs
     */
    @Transactional(readOnly = true)
    public Set<UUID> getAffectedMenuItemIds(UUID ingredientId) {
        Set<UUID> affectedMenuItemIds = new HashSet<>();
        collectAffectedMenuItems(ingredientId, affectedMenuItemIds, new HashSet<>());
        return affectedMenuItemIds;
    }

    private void collectAffectedMenuItems(UUID ingredientId, Set<UUID> affectedMenuItemIds, Set<UUID> visitedRecipes) {
        List<RecipeComponent> components = recipeComponentRepository.findByIngredientId(ingredientId);
        for (RecipeComponent component : components) {
            Recipe recipe = component.getRecipe();
            if (visitedRecipes.contains(recipe.getId())) continue;
            visitedRecipes.add(recipe.getId());

            if (recipe.getMenuItem() != null) {
                affectedMenuItemIds.add(recipe.getMenuItem().getId());
            }

            // Recurse for recipes that use THIS recipe as a sub-recipe
            collectAffectedMenuItemsFromSubRecipe(recipe.getId(), affectedMenuItemIds, visitedRecipes);
        }
    }

    private void collectAffectedMenuItemsFromSubRecipe(UUID subRecipeId, Set<UUID> affectedMenuItemIds, Set<UUID> visitedRecipes) {
        List<RecipeComponent> components = recipeComponentRepository.findBySubRecipeId(subRecipeId);
        for (RecipeComponent component : components) {
            Recipe recipe = component.getRecipe();
            if (visitedRecipes.contains(recipe.getId())) continue;
            visitedRecipes.add(recipe.getId());

            if (recipe.getMenuItem() != null) {
                affectedMenuItemIds.add(recipe.getMenuItem().getId());
            }

            collectAffectedMenuItemsFromSubRecipe(recipe.getId(), affectedMenuItemIds, visitedRecipes);
        }
    }
}
