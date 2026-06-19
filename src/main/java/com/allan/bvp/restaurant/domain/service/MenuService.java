package com.allan.bvp.restaurant.domain.service;

import com.allan.bvp.restaurant.domain.enums.ServicePeriod;
import com.allan.bvp.restaurant.domain.model.MenuItem;
import com.allan.bvp.restaurant.domain.model.OutboxEvent;
import com.allan.bvp.restaurant.domain.model.Recipe;
import com.allan.bvp.restaurant.domain.repository.MenuItemRepository;
import com.allan.bvp.restaurant.domain.repository.OutboxRepository;
import com.allan.bvp.restaurant.domain.repository.RecipeRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for managing menu items and their associated recipes.
 */
@Service
@RequiredArgsConstructor
public class MenuService {

    private final MenuItemRepository menuItemRepository;
    private final RecipeRepository recipeRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new menu item and records a MenuItemPublished event in the outbox.
     *
     * @param venueId       the ID of the venue
     * @param name          the name of the menu item
     * @param priceMinor    the price in minor units
     * @param servicePeriod the period when this item is available
     * @return the created menu item
     */
    @Transactional
    public MenuItem createMenuItem(String venueId, String name, Long priceMinor, ServicePeriod servicePeriod) {
        MenuItem menuItem = MenuItem.builder()
                .venueId(venueId)
                .name(name)
                .priceMinor(priceMinor)
                .servicePeriod(servicePeriod)
                .active(true)
                .build();
        MenuItem saved = menuItemRepository.save(menuItem);

        try {
            OutboxEvent event = OutboxEvent.builder()
                    .eventType("MenuItemPublished")
                    .payload(objectMapper.writeValueAsString(saved))
                    .build();
            outboxRepository.save(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize MenuItem for outbox", e);
        }

        return saved;
    }

    /**
     * Attaches a new recipe to an existing menu item.
     *
     * @param menuItemId the ID of the menu item
     * @param yield      the yield quantity for the recipe
     * @return the created recipe
     */
    @Transactional
    public Recipe attachRecipe(UUID menuItemId, int yield) {
        MenuItem menuItem = menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new IllegalArgumentException("MenuItem not found"));

        Recipe recipe = Recipe.builder()
                .menuItem(menuItem)
                .yield(yield)
                .incomplete(true)
                .build();

        return recipeRepository.save(recipe);
    }
}
