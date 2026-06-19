package com.allan.bvp.restaurant.domain.service;

import com.allan.bvp.restaurant.application.dto.FlattenedComponentDTO;
import com.allan.bvp.restaurant.domain.enums.MovementType;
import com.allan.bvp.restaurant.domain.model.Ingredient;
import com.allan.bvp.restaurant.domain.model.StockItem;
import com.allan.bvp.restaurant.domain.model.StockMovement;
import com.allan.bvp.restaurant.domain.repository.OutboxRepository;
import com.allan.bvp.restaurant.domain.repository.StockItemRepository;
import com.allan.bvp.restaurant.domain.repository.StockMovementRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link StockService}.
 */
@ExtendWith(MockitoExtension.class)
public class StockServiceTest {

    @Mock
    private StockItemRepository stockItemRepository;

    @Mock
    private StockMovementRepository stockMovementRepository;

    @Mock
    private IngredientService ingredientService;

    @Mock
    private RecipeService recipeService;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private StockService stockService;

    @Test
    void recordReceipt_ShouldUpdateStockAndCalculateWAC() {
        // Arrange
        String venueId = "venue-1";
        UUID ingredientId = UUID.randomUUID();
        String idempotencyKey = "receipt-123";
        Long receivedQty = 100L;
        Long purchasePrice = 200L; // $2.00 per unit

        Ingredient ingredient = Ingredient.builder().id(ingredientId).name("Salt").build();
        StockItem stockItem = StockItem.builder()
                .id(UUID.randomUUID())
                .venueId(venueId)
                .ingredient(ingredient)
                .onHandMinor(50L)
                .weightedAverageCostMinor(100L) // $1.00 per unit
                .parLevelMinor(10L)
                .build();

        // Old total cost = 50 * 100 = 5000
        // New receipt cost = 100 * 200 = 20000
        // Total cost = 25000
        // Total qty = 150
        // New WAC = 25000 / 150 = 166.66... -> 167 (rounded or truncated? Let's say floor or round?)
        // Let's use standard WAC formula. (50*100 + 100*200) / (50+100) = 166

        when(stockMovementRepository.existsByVenueIdAndStockItemIdAndIdempotencyKey(venueId, stockItem.getId(), idempotencyKey))
                .thenReturn(false);
        when(stockItemRepository.findByVenueIdAndIngredientId(venueId, ingredientId))
                .thenReturn(Optional.of(stockItem));
        when(stockItemRepository.save(any(StockItem.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        stockService.recordReceipt(venueId, ingredientId, receivedQty, purchasePrice, idempotencyKey);

        // Assert
        assertEquals(150L, stockItem.getOnHandMinor());
        assertEquals(166L, stockItem.getWeightedAverageCostMinor());
        
        verify(stockMovementRepository).save(argThat(m -> 
            m.getType() == MovementType.RECEIPT &&
            m.getQtyMinor().equals(receivedQty) &&
            m.getIdempotencyKey().equals(idempotencyKey)
        ));
        
        verify(ingredientService).updateIngredientCost(ingredientId, 166L);
        verify(outboxRepository, atLeastOnce()).save(any());
    }

    @Test
    void recordReceipt_ShouldBeIdempotent() {
        // Arrange
        String venueId = "venue-1";
        UUID ingredientId = UUID.randomUUID();
        String idempotencyKey = "receipt-123";
        
        StockItem stockItem = StockItem.builder().id(UUID.randomUUID()).build();
        when(stockItemRepository.findByVenueIdAndIngredientId(venueId, ingredientId))
                .thenReturn(Optional.of(stockItem));
        when(stockMovementRepository.existsByVenueIdAndStockItemIdAndIdempotencyKey(venueId, stockItem.getId(), idempotencyKey))
                .thenReturn(true);

        // Act
        stockService.recordReceipt(venueId, ingredientId, 100L, 200L, idempotencyKey);

        // Assert
        verify(stockItemRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void recordWastage_ShouldDecreaseStock() {
        // Arrange
        String venueId = "venue-1";
        UUID ingredientId = UUID.randomUUID();
        String idempotencyKey = "wastage-123";
        Long wastageQty = 20L;

        StockItem stockItem = StockItem.builder()
                .id(UUID.randomUUID())
                .venueId(venueId)
                .onHandMinor(100L)
                .parLevelMinor(10L)
                .build();

        when(stockItemRepository.findByVenueIdAndIngredientId(venueId, ingredientId))
                .thenReturn(Optional.of(stockItem));
        when(stockMovementRepository.existsByVenueIdAndStockItemIdAndIdempotencyKey(venueId, stockItem.getId(), idempotencyKey))
                .thenReturn(false);

        // Act
        stockService.recordWastage(venueId, ingredientId, wastageQty, idempotencyKey);

        // Assert
        assertEquals(80L, stockItem.getOnHandMinor());
        verify(stockMovementRepository).save(argThat(m -> 
            m.getType() == MovementType.WASTAGE &&
            m.getQtyMinor().equals(wastageQty)
        ));
    }

    @Test
    void recordAdjustment_ShouldSetStockAndRecordDifference() {
        // Arrange
        String venueId = "venue-1";
        UUID ingredientId = UUID.randomUUID();
        String idempotencyKey = "adj-123";
        Long newActualQty = 90L;

        StockItem stockItem = StockItem.builder()
                .id(UUID.randomUUID())
                .venueId(venueId)
                .onHandMinor(100L)
                .parLevelMinor(10L)
                .build();

        when(stockItemRepository.findByVenueIdAndIngredientId(venueId, ingredientId))
                .thenReturn(Optional.of(stockItem));
        when(stockMovementRepository.existsByVenueIdAndStockItemIdAndIdempotencyKey(venueId, stockItem.getId(), idempotencyKey))
                .thenReturn(false);

        // Act
        stockService.recordAdjustment(venueId, ingredientId, newActualQty, idempotencyKey);

        // Assert
        assertEquals(90L, stockItem.getOnHandMinor());
        verify(stockMovementRepository).save(argThat(m -> 
            m.getType() == MovementType.ADJUSTMENT &&
            m.getQtyMinor().equals(-10L) // Difference is -10
        ));
    }

    @Test
    void depleteStock_ShouldDecreaseAllLeafComponents() {
        // Arrange
        String venueId = "venue-1";
        UUID recipeId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        int quantity = 2; // Order 2 items

        Ingredient ing1 = Ingredient.builder().id(UUID.randomUUID()).name("Ing1").build();
        Ingredient ing2 = Ingredient.builder().id(UUID.randomUUID()).name("Ing2").build();

        StockItem si1 = StockItem.builder().id(UUID.randomUUID()).onHandMinor(100L).parLevelMinor(10L).build();
        StockItem si2 = StockItem.builder().id(UUID.randomUUID()).onHandMinor(50L).parLevelMinor(10L).build();

        when(recipeService.getFlattenedComponents(recipeId)).thenReturn(List.of(
                new FlattenedComponentDTO(ing1, 10L), // 10 units per dish
                new FlattenedComponentDTO(ing2, 5L)   // 5 units per dish
        ));

        when(stockItemRepository.findByVenueIdAndIngredientId(venueId, ing1.getId())).thenReturn(Optional.of(si1));
        when(stockItemRepository.findByVenueIdAndIngredientId(venueId, ing2.getId())).thenReturn(Optional.of(si2));
        
        // Idempotency check for each component
        when(stockMovementRepository.existsByVenueIdAndStockItemIdAndIdempotencyKey(eq(venueId), any(UUID.class), contains(orderItemId.toString())))
                .thenReturn(false);

        // Act
        stockService.depleteStock(venueId, recipeId, quantity, orderItemId);

        // Assert
        assertEquals(80L, si1.getOnHandMinor()); // 100 - (10 * 2)
        assertEquals(40L, si2.getOnHandMinor()); // 50 - (5 * 2)
        
        verify(stockMovementRepository, times(2)).save(any(StockMovement.class));
    }

    @Test
    void verifyOnHand_ShouldCompareSumWithCachedValue() {
        // Arrange
        UUID stockItemId = UUID.randomUUID();
        StockItem stockItem = StockItem.builder()
                .id(stockItemId)
                .onHandMinor(150L)
                .build();

        StockMovement m1 = StockMovement.builder().type(MovementType.RECEIPT).qtyMinor(100L).build();
        StockMovement m2 = StockMovement.builder().type(MovementType.RECEIPT).qtyMinor(100L).build();
        StockMovement m3 = StockMovement.builder().type(MovementType.DEPLETION).qtyMinor(50L).build();

        when(stockItemRepository.findById(stockItemId)).thenReturn(Optional.of(stockItem));
        when(stockMovementRepository.findByStockItemIdOrderByCreatedAtAsc(stockItemId))
                .thenReturn(List.of(m1, m2, m3));

        // Act
        boolean result = stockService.verifyOnHand(stockItemId);

        // Assert
        assertTrue(result); // 100 + 100 - 50 = 150
    }
}
