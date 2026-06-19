package com.allan.bvp.restaurant.domain.service;

import com.allan.bvp.restaurant.application.dto.FlattenedComponentDTO;
import com.allan.bvp.restaurant.domain.event.StockLevelChangedInternalEvent;
import com.allan.bvp.restaurant.domain.model.*;
import com.allan.bvp.restaurant.domain.repository.MenuAvailabilityRepository;
import com.allan.bvp.restaurant.domain.repository.MenuItemRepository;
import com.allan.bvp.restaurant.domain.repository.OutboxRepository;
import com.allan.bvp.restaurant.domain.repository.StockItemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class MenuAvailabilityServiceTest {

    @Mock
    private MenuAvailabilityRepository availabilityRepository;
    @Mock
    private MenuItemRepository menuItemRepository;
    @Mock
    private RecipeService recipeService;
    @Mock
    private StockItemRepository stockItemRepository;
    @Mock
    private OutboxRepository outboxRepository;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private MenuAvailabilityService availabilityService;

    private UUID menuItemId;
    private String venueId;
    private MenuItem menuItem;
    private Recipe recipe;

    @BeforeEach
    void setUp() {
        menuItemId = UUID.randomUUID();
        venueId = "venue-1";
        menuItem = MenuItem.builder()
                .id(menuItemId)
                .venueId(venueId)
                .name("Test Dish")
                .active(true)
                .build();
        recipe = Recipe.builder().id(UUID.randomUUID()).menuItem(menuItem).yield(1).build();
        menuItem.setRecipe(recipe);
    }

    @Test
    void updateAvailability_ShouldComputeMakeableQuantityAndSave() throws Exception {
        // Arrange
        Ingredient ing1 = Ingredient.builder().id(UUID.randomUUID()).name("Ing 1").build();
        Ingredient ing2 = Ingredient.builder().id(UUID.randomUUID()).name("Ing 2").build();
        
        // Dish requires 100 units of ing1 and 200 units of ing2
        List<FlattenedComponentDTO> components = List.of(
                new FlattenedComponentDTO(ing1, 100L),
                new FlattenedComponentDTO(ing2, 200L)
        );
        
        when(menuItemRepository.findById(menuItemId)).thenReturn(Optional.of(menuItem));
        when(recipeService.getFlattenedComponents(recipe.getId())).thenReturn(components);
        
        // Stock: 500 of ing1, 1000 of ing2
        // Makeable from ing1: 500/100 = 5
        // Makeable from ing2: 1000/200 = 5
        StockItem stock1 = StockItem.builder().ingredient(ing1).onHandMinor(500L).build();
        StockItem stock2 = StockItem.builder().ingredient(ing2).onHandMinor(1000L).build();
        
        when(stockItemRepository.findByVenueIdAndIngredientId(venueId, ing1.getId())).thenReturn(Optional.of(stock1));
        when(stockItemRepository.findByVenueIdAndIngredientId(venueId, ing2.getId())).thenReturn(Optional.of(stock2));
        
        when(availabilityRepository.findById(menuItemId)).thenReturn(Optional.empty());
        when(availabilityRepository.save(any(MenuAvailability.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        availabilityService.updateAvailability(menuItemId);

        // Assert
        ArgumentCaptor<MenuAvailability> captor = ArgumentCaptor.forClass(MenuAvailability.class);
        verify(availabilityRepository).save(captor.capture());
        MenuAvailability saved = captor.getValue();
        
        assertEquals(5, saved.getMakeableQuantity());
        assertTrue(saved.isAvailable());
    }

    @Test
    void updateAvailability_ShouldHandleZeroStock() throws Exception {
        // Arrange
        Ingredient ing1 = Ingredient.builder().id(UUID.randomUUID()).name("Ing 1").build();
        List<FlattenedComponentDTO> components = List.of(new FlattenedComponentDTO(ing1, 100L));
        
        when(menuItemRepository.findById(menuItemId)).thenReturn(Optional.of(menuItem));
        when(recipeService.getFlattenedComponents(recipe.getId())).thenReturn(components);
        
        StockItem stock1 = StockItem.builder().ingredient(ing1).onHandMinor(50L).build(); // 50/100 = 0
        when(stockItemRepository.findByVenueIdAndIngredientId(venueId, ing1.getId())).thenReturn(Optional.of(stock1));
        
        // Existing available item
        MenuAvailability existing = MenuAvailability.builder()
                .menuItemId(menuItemId)
                .menuItem(menuItem)
                .available(true)
                .build();
        when(availabilityRepository.findById(menuItemId)).thenReturn(Optional.of(existing));
        when(availabilityRepository.save(any(MenuAvailability.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        availabilityService.updateAvailability(menuItemId);

        // Assert
        ArgumentCaptor<MenuAvailability> captor = ArgumentCaptor.forClass(MenuAvailability.class);
        verify(availabilityRepository).save(captor.capture());
        MenuAvailability saved = captor.getValue();
        
        assertEquals(0, saved.getMakeableQuantity());
        assertFalse(saved.isAvailable());
        
        // Should emit MenuItemUnavailable event
        verify(outboxRepository).save(argThat(event -> event.getEventType().equals("MenuItemUnavailable")));
    }

    @Test
    void setManualOverride_ShouldOverrideComputedValue() throws Exception {
        // Arrange
        MenuAvailability existing = MenuAvailability.builder()
                .menuItemId(menuItemId)
                .menuItem(menuItem)
                .makeableQuantity(10)
                .available(true)
                .manualOverride(false)
                .build();
        
        when(availabilityRepository.findById(menuItemId)).thenReturn(Optional.of(existing));
        when(availabilityRepository.save(any(MenuAvailability.class))).thenAnswer(i -> i.getArgument(0));

        // Act: Manually 86 the item
        availabilityService.setManualOverride(menuItemId, false);

        // Assert
        verify(availabilityRepository).save(argThat(a -> a.isManualOverride() && !a.isAvailable()));
        verify(outboxRepository).save(argThat(event -> event.getEventType().equals("MenuItemUnavailable")));
    }

    @Test
    void clearManualOverride_ShouldReturnToComputed() throws Exception {
        // Arrange
        MenuAvailability existing = MenuAvailability.builder()
                .menuItemId(menuItemId)
                .menuItem(menuItem)
                .makeableQuantity(10)
                .available(false)
                .manualOverride(true)
                .overrideState(false)
                .build();
        
        when(availabilityRepository.findById(menuItemId)).thenReturn(Optional.of(existing));
        when(menuItemRepository.findById(menuItemId)).thenReturn(Optional.of(menuItem)); // Needed by updateAvailability
        when(recipeService.getFlattenedComponents(any())).thenReturn(List.of()); // Mock recipe service
        when(availabilityRepository.save(any(MenuAvailability.class))).thenAnswer(i -> i.getArgument(0));
        
        // Act
        availabilityService.clearManualOverride(menuItemId);

        // Assert
        verify(availabilityRepository, atLeastOnce()).save(argThat(a -> !a.isManualOverride()));
    }

    @Test
    void onStockLevelChanged_ShouldTriggerUpdateForAffectedItems() {
        // Arrange
        UUID ingredientId = UUID.randomUUID();
        Ingredient ingredient = Ingredient.builder().id(ingredientId).build();
        StockItem stockItem = StockItem.builder().ingredient(ingredient).build();
        StockLevelChangedInternalEvent event = new StockLevelChangedInternalEvent(stockItem);
        
        java.util.Set<UUID> affectedIds = java.util.Set.of(menuItemId);
        when(recipeService.getAffectedMenuItemIds(ingredientId)).thenReturn(affectedIds);
        
        // Mock updateAvailability dependencies
        when(menuItemRepository.findById(menuItemId)).thenReturn(Optional.of(menuItem));
        when(recipeService.getFlattenedComponents(recipe.getId())).thenReturn(List.of());
        when(availabilityRepository.findById(menuItemId)).thenReturn(Optional.empty());
        
        // Act
        availabilityService.onStockLevelChanged(event);

        // Assert
        verify(availabilityRepository).save(any(MenuAvailability.class));
    }
}
