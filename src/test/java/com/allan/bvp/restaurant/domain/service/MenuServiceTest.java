package com.allan.bvp.restaurant.domain.service;

import com.allan.bvp.restaurant.domain.enums.ServicePeriod;
import com.allan.bvp.restaurant.domain.model.MenuItem;
import com.allan.bvp.restaurant.domain.model.OutboxEvent;
import com.allan.bvp.restaurant.domain.model.Recipe;
import com.allan.bvp.restaurant.domain.repository.MenuItemRepository;
import com.allan.bvp.restaurant.domain.repository.OutboxRepository;
import com.allan.bvp.restaurant.domain.repository.RecipeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MenuService}.
 */
@ExtendWith(MockitoExtension.class)
public class MenuServiceTest {

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private RecipeRepository recipeRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private MenuService menuService;

    @Test
    void createMenuItem_ShouldSaveAndReturnMenuItem() {
        // Arrange
        String venueId = "venue-1";
        String name = "Pasta Carbonara";
        Long price = 1500L; // $15.00
        ServicePeriod period = ServicePeriod.DINNER;

        MenuItem expected = MenuItem.builder()
                .venueId(venueId)
                .name(name)
                .priceMinor(price)
                .servicePeriod(period)
                .active(true)
                .build();

        when(menuItemRepository.save(any(MenuItem.class))).thenReturn(expected);
        when(outboxRepository.save(any(OutboxEvent.class))).thenReturn(new OutboxEvent());

        // Act
        MenuItem result = menuService.createMenuItem(venueId, name, price, period);

        // Assert
        assertNotNull(result);
        assertEquals(name, result.getName());
        assertEquals(price, result.getPriceMinor());
        assertEquals(period, result.getServicePeriod());
        assertTrue(result.isActive());
        assertEquals(venueId, result.getVenueId());

        verify(menuItemRepository).save(any(MenuItem.class));
        verify(outboxRepository).save(any(OutboxEvent.class));
    }

    @Test
    void attachRecipe_ShouldCreateRecipeAndReturnIt() {
        // Arrange
        UUID menuItemId = UUID.randomUUID();
        MenuItem menuItem = MenuItem.builder().id(menuItemId).name("Pasta").build();
        int yield = 1;

        when(menuItemRepository.findById(menuItemId)).thenReturn(java.util.Optional.of(menuItem));
        when(recipeRepository.save(any(Recipe.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Recipe result = menuService.attachRecipe(menuItemId, yield);

        // Assert
        assertNotNull(result);
        assertEquals(menuItem, result.getMenuItem());
        assertEquals(yield, result.getYield());
        assertTrue(result.isIncomplete());

        verify(recipeRepository).save(any(Recipe.class));
    }
}
