package com.allan.bvp.restaurant.domain.service;

import com.allan.bvp.restaurant.application.dto.FlattenedComponentDTO;
import com.allan.bvp.restaurant.domain.event.MenuItemAvailabilityEvent;
import com.allan.bvp.restaurant.domain.event.StockLevelChangedInternalEvent;
import com.allan.bvp.restaurant.domain.model.*;
import com.allan.bvp.restaurant.domain.repository.MenuAvailabilityRepository;
import com.allan.bvp.restaurant.domain.repository.MenuItemRepository;
import com.allan.bvp.restaurant.domain.repository.OutboxRepository;
import com.allan.bvp.restaurant.domain.repository.StockItemRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service for managing menu item availability.
 * It computes makeable quantities and handles manual overrides.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MenuAvailabilityService {

    private final MenuAvailabilityRepository availabilityRepository;
    private final MenuItemRepository menuItemRepository;
    private final RecipeService recipeService;
    private final StockItemRepository stockItemRepository; // Wait, I need a way to get stock on hand
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * Updates the availability for a specific menu item.
     * It recalculates the makeable quantity based on current stock levels of all leaf ingredients.
     * 
     * The makeable quantity is computed as the minimum amount of portions that can be prepared
     * given the on-hand stock of each required leaf ingredient.
     * 
     * If the availability status changes (e.g., from available to unavailable), an event
     * is recorded in the outbox.
     *
     * @param menuItemId the ID of the menu item to update
     */
    @Transactional
    public void updateAvailability(UUID menuItemId) {
        MenuItem menuItem = menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new IllegalArgumentException("MenuItem not found"));

        if (menuItem.getRecipe() == null) {
            log.warn("MenuItem {} has no recipe, skipping availability update", menuItemId);
            return;
        }

        List<FlattenedComponentDTO> components = recipeService.getFlattenedComponents(menuItem.getRecipe().getId());

        long minMakeable = Long.MAX_VALUE;
        if (components.isEmpty()) {
            minMakeable = 0;
        } else {
            for (FlattenedComponentDTO component : components) {
                StockItem stock = stockItemRepository.findByVenueIdAndIngredientId(menuItem.getVenueId(), component.getIngredient().getId())
                        .orElse(null);

                long onHand = (stock != null) ? stock.getOnHandMinor() : 0;
                long required = component.getTotalQtyMinor();

                if (required > 0) {
                    long makeable = onHand / required;
                    if (makeable < minMakeable) {
                        minMakeable = makeable;
                    }
                }
            }
        }

        if (minMakeable == Long.MAX_VALUE) minMakeable = 0;

        MenuAvailability availability = availabilityRepository.findById(menuItemId)
                .orElse(MenuAvailability.builder()
                        .menuItemId(menuItemId)
                        .menuItem(menuItem)
                        .build());

        boolean previousAvailable = availability.isAvailable();
        availability.setMakeableQuantity(minMakeable);
        availability.updateStatus();

        availabilityRepository.save(availability);

        if (previousAvailable != availability.isAvailable()) {
            emitAvailabilityEvent(availability);
        }
    }

    /**
     * Manually sets the availability for a specific menu item, overriding computed values.
     * This is used when a manager wants to "86" (mark as unavailable) an item manually,
     * or restore it even if the system thinks it's out of stock.
     *
     * @param menuItemId the ID of the menu item
     * @param available  the desired availability status (true for available, false for 86'd)
     */
    @Transactional
    public void setManualOverride(UUID menuItemId, boolean available) {
        MenuAvailability availability = availabilityRepository.findById(menuItemId)
                .orElseThrow(() -> new IllegalArgumentException("MenuAvailability not found for item: " + menuItemId));

        boolean previousAvailable = availability.isAvailable();
        availability.setManualOverride(true);
        availability.setOverrideState(available);
        availability.updateStatus();

        availabilityRepository.save(availability);

        if (previousAvailable != availability.isAvailable()) {
            emitAvailabilityEvent(availability);
        }
    }

    /**
     * Clears the manual override for a specific menu item, returning to computed availability
     * based on current stock levels.
     *
     * @param menuItemId the ID of the menu item
     */
    @Transactional
    public void clearManualOverride(UUID menuItemId) {
        MenuAvailability availability = availabilityRepository.findById(menuItemId)
                .orElseThrow(() -> new IllegalArgumentException("MenuAvailability not found for item: " + menuItemId));

        availability.setManualOverride(false);
        // Trigger update to recompute based on stock
        updateAvailability(menuItemId);
    }

    /**
     * Handles stock level changes by updating all affected menu items.
     * This method is triggered by StockLevelChangedInternalEvent.
     * It identifies all menu items that use the modified ingredient in their recipes
     * and triggers an availability recalculation for each.
     *
     * @param event the stock level changed event
     */
    @EventListener
    public void onStockLevelChanged(StockLevelChangedInternalEvent event) {
        UUID ingredientId = event.getStockItem().getIngredient().getId();
        Set<UUID> affectedMenuItemIds = recipeService.getAffectedMenuItemIds(ingredientId);
        log.info("Stock changed for ingredient {}, updating {} affected menu items", ingredientId, affectedMenuItemIds.size());
        for (UUID menuItemId : affectedMenuItemIds) {
            updateAvailability(menuItemId);
        }
    }

    private void emitAvailabilityEvent(MenuAvailability availability) {
        String eventType = availability.isAvailable() ? "MenuItemAvailable" : "MenuItemUnavailable";
        MenuItemAvailabilityEvent payload = MenuItemAvailabilityEvent.builder()
                .menuItemId(availability.getMenuItemId())
                .venueId(availability.getMenuItem().getVenueId())
                .available(availability.isAvailable())
                .makeableQuantity(availability.getMakeableQuantity())
                .build();

        try {
            OutboxEvent event = OutboxEvent.builder()
                    .eventType(eventType)
                    .payload(objectMapper.writeValueAsString(payload))
                    .build();
            outboxRepository.save(event);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize availability event", e);
        }
    }
}
