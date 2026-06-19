package com.allan.bvp.restaurant.domain.service;

import com.allan.bvp.restaurant.application.dto.FlattenedComponentDTO;
import com.allan.bvp.restaurant.domain.enums.MovementType;
import com.allan.bvp.restaurant.domain.event.StockLevelChangedInternalEvent;
import com.allan.bvp.restaurant.domain.model.OutboxEvent;
import com.allan.bvp.restaurant.domain.model.StockItem;
import com.allan.bvp.restaurant.domain.model.StockMovement;
import com.allan.bvp.restaurant.domain.repository.OutboxRepository;
import com.allan.bvp.restaurant.domain.repository.StockItemRepository;
import com.allan.bvp.restaurant.domain.repository.StockMovementRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Service responsible for managing inventory levels and stock movements.
 * This service ensures that all stock changes are recorded in an append-only ledger
 * and that on-hand projections are updated transactionally.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final IngredientService ingredientService;
    private final RecipeService recipeService;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Records the receipt of goods, increasing the stock on hand and updating the weighted-average cost (WAC).
     * 
     * @param venueId The ID of the venue receiving the goods.
     * @param ingredientId The ID of the ingredient being received.
     * @param qty The quantity received (in minor units).
     * @param purchasePrice The price at which the goods were purchased (in minor units).
     * @param idempotencyKey A unique key to prevent duplicate processing of the same receipt.
     */
    @Transactional
    public void recordReceipt(String venueId, UUID ingredientId, Long qty, Long purchasePrice, String idempotencyKey) {
        StockItem stockItem = stockItemRepository.findByVenueIdAndIngredientId(venueId, ingredientId)
                .orElseThrow(() -> new IllegalArgumentException("StockItem not found for ingredient: " + ingredientId));

        if (stockMovementRepository.existsByVenueIdAndStockItemIdAndIdempotencyKey(venueId, stockItem.getId(), idempotencyKey)) {
            log.info("Receipt already processed for idempotencyKey: {}", idempotencyKey);
            return;
        }

        // Calculate new Weighted Average Cost (WAC)
        // Formula: (Current Total Value + New Receipt Value) / (Current Qty + New Qty)
        long currentQty = stockItem.getOnHandMinor();
        long currentWac = stockItem.getWeightedAverageCostMinor();
        
        long currentValue = currentQty * currentWac;
        long newValue = qty * purchasePrice;
        long totalQty = currentQty + qty;
        
        long newWac = (currentValue + newValue) / totalQty;

        // Update StockItem projection
        stockItem.setOnHandMinor(totalQty);
        stockItem.setWeightedAverageCostMinor(newWac);
        stockItemRepository.save(stockItem);

        // Record Movement in ledger
        StockMovement movement = StockMovement.builder()
                .venueId(venueId)
                .stockItem(stockItem)
                .type(MovementType.RECEIPT)
                .qtyMinor(qty)
                .idempotencyKey(idempotencyKey)
                .build();
        stockMovementRepository.save(movement);

        // Sync cost back to Ingredient for recipe calculations
        ingredientService.updateIngredientCost(ingredientId, newWac);

        // Publish events
        emitStockLevelEvents(stockItem);
    }

    /**
     * Records wastage of an ingredient, decreasing the stock on hand.
     *
     * @param venueId        the ID of the venue
     * @param ingredientId   the ID of the ingredient
     * @param qty            the quantity wasted in minor units
     * @param idempotencyKey a unique key to prevent duplicate processing
     */
    @Transactional
    public void recordWastage(String venueId, UUID ingredientId, Long qty, String idempotencyKey) {
        StockItem stockItem = stockItemRepository.findByVenueIdAndIngredientId(venueId, ingredientId)
                .orElseThrow(() -> new IllegalArgumentException("StockItem not found"));

        if (stockMovementRepository.existsByVenueIdAndStockItemIdAndIdempotencyKey(venueId, stockItem.getId(), idempotencyKey)) {
            return;
        }

        stockItem.setOnHandMinor(stockItem.getOnHandMinor() - qty);
        stockItemRepository.save(stockItem);

        StockMovement movement = StockMovement.builder()
                .venueId(venueId)
                .stockItem(stockItem)
                .type(MovementType.WASTAGE)
                .qtyMinor(qty)
                .idempotencyKey(idempotencyKey)
                .build();
        stockMovementRepository.save(movement);

        emitStockLevelEvents(stockItem);
    }

    /**
     * Records a manual stock adjustment, setting the stock on hand to a specific value.
     *
     * @param venueId        the ID of the venue
     * @param ingredientId   the ID of the ingredient
     * @param newActualQty   the new physical count in minor units
     * @param idempotencyKey a unique key to prevent duplicate processing
     */
    @Transactional
    public void recordAdjustment(String venueId, UUID ingredientId, Long newActualQty, String idempotencyKey) {
        StockItem stockItem = stockItemRepository.findByVenueIdAndIngredientId(venueId, ingredientId)
                .orElseThrow(() -> new IllegalArgumentException("StockItem not found"));

        if (stockMovementRepository.existsByVenueIdAndStockItemIdAndIdempotencyKey(venueId, stockItem.getId(), idempotencyKey)) {
            return;
        }

        long diff = newActualQty - stockItem.getOnHandMinor();
        stockItem.setOnHandMinor(newActualQty);
        stockItemRepository.save(stockItem);

        StockMovement movement = StockMovement.builder()
                .venueId(venueId)
                .stockItem(stockItem)
                .type(MovementType.ADJUSTMENT)
                .qtyMinor(diff) // Record the delta
                .idempotencyKey(idempotencyKey)
                .build();
        stockMovementRepository.save(movement);

        emitStockLevelEvents(stockItem);
    }

    /**
     * Depletes stock based on a recipe when an order item is confirmed.
     * This method expands the recipe to its leaf ingredients and records a DEPLETION movement for each.
     * 
     * @param venueId The venue where the depletion occurs.
     * @param recipeId The ID of the recipe being prepared.
     * @param quantity The number of portions prepared.
     * @param orderItemId The ID of the order item, used for idempotency.
     */
    @Transactional
    public void depleteStock(String venueId, UUID recipeId, int quantity, UUID orderItemId) {
        List<FlattenedComponentDTO> components = recipeService.getFlattenedComponents(recipeId);

        for (FlattenedComponentDTO component : components) {
            UUID ingredientId = component.getIngredient().getId();
            StockItem stockItem = stockItemRepository.findByVenueIdAndIngredientId(venueId, ingredientId)
                    .orElseThrow(() -> new IllegalArgumentException("StockItem not found for ingredient: " + ingredientId));

            // Idempotency key per ingredient for this order item
            String idempotencyKey = "depletion-" + orderItemId + "-" + ingredientId;

            if (stockMovementRepository.existsByVenueIdAndStockItemIdAndIdempotencyKey(venueId, stockItem.getId(), idempotencyKey)) {
                continue;
            }

            long totalDepletionQty = component.getTotalQtyMinor() * quantity;
            stockItem.setOnHandMinor(stockItem.getOnHandMinor() - totalDepletionQty);
            stockItemRepository.save(stockItem);

            StockMovement movement = StockMovement.builder()
                    .venueId(venueId)
                    .stockItem(stockItem)
                    .type(MovementType.DEPLETION)
                    .qtyMinor(totalDepletionQty)
                    .idempotencyKey(idempotencyKey)
                    .sourceEvent(orderItemId)
                    .build();
            stockMovementRepository.save(movement);

            emitStockLevelEvents(stockItem);
        }
    }

    /**
     * Verifies that the cached on-hand value in StockItem matches the sum of movements in the ledger.
     * 
     * @param stockItemId The ID of the StockItem to verify.
     * @return true if the values match, false otherwise.
     */
    @Transactional(readOnly = true)
    public boolean verifyOnHand(UUID stockItemId) {
        StockItem stockItem = stockItemRepository.findById(stockItemId)
                .orElseThrow(() -> new IllegalArgumentException("StockItem not found"));

        List<StockMovement> movements = stockMovementRepository.findByStockItemIdOrderByCreatedAtAsc(stockItemId);

        long sum = 0;
        for (StockMovement movement : movements) {
            switch (movement.getType()) {
                case RECEIPT, ADJUSTMENT -> sum += movement.getQtyMinor();
                case DEPLETION, WASTAGE -> sum -= movement.getQtyMinor();
            }
        }

        return sum == stockItem.getOnHandMinor();
    }

    private void emitStockLevelEvents(StockItem stockItem) {
        try {
            // StockLevelChanged event
            OutboxEvent stockEvent = OutboxEvent.builder()
                    .eventType("StockLevelChanged")
                    .payload(objectMapper.writeValueAsString(stockItem))
                    .build();
            outboxRepository.save(stockEvent);

            // Publish internal event for immediate read-model updates
            eventPublisher.publishEvent(new StockLevelChangedInternalEvent(stockItem));

            // ParLevelBreached event if applicable
            if (stockItem.getOnHandMinor() < stockItem.getParLevelMinor()) {
                OutboxEvent parEvent = OutboxEvent.builder()
                        .eventType("ParLevelBreached")
                        .payload(objectMapper.writeValueAsString(stockItem))
                        .build();
                outboxRepository.save(parEvent);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize stock event", e);
        }
    }
}
