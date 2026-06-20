package com.allan.bvp.restaurant.domain.service;

import com.allan.bvp.restaurant.domain.enums.Unit;
import com.allan.bvp.restaurant.domain.model.*;
import com.allan.bvp.restaurant.domain.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
public class StockServiceConcurrencyTest {

    @Autowired
    private StockService stockService;

    @MockitoBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private StockItemRepository stockItemRepository;

    @Autowired
    private IngredientRepository ingredientRepository;

    @Autowired
    private MenuItemRepository menuItemRepository;

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private RecipeService recipeService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void depleteStock_ShouldHandleConcurrencyWithOptimisticLocking() throws Exception {
        // Arrange
        String venueId = "CONCURRENCY-TEST-VENUE";
        TransactionTemplate tt = new TransactionTemplate(transactionManager);

        UUID recipeId = tt.execute(status -> {
            Ingredient ingredient = Ingredient.builder()
                    .venueId(venueId)
                    .name("Test Ingredient")
                    .baseUnit(Unit.GRAM)
                    .unitCostMinor(100L)
                    .build();
            ingredient = ingredientRepository.save(ingredient);

            StockItem stockItem = StockItem.builder()
                    .venueId(venueId)
                    .ingredient(ingredient)
                    .onHandMinor(1000L)
                    .weightedAverageCostMinor(100L)
                    .parLevelMinor(100L)
                    .build();
            stockItemRepository.save(stockItem);

            MenuItem menuItem = MenuItem.builder()
                    .venueId(venueId)
                    .name("Test Dish")
                    .priceMinor(500L)
                    .active(true)
                    .build();
            menuItem = menuItemRepository.save(menuItem);

            Recipe recipe = Recipe.builder()
                    .menuItem(menuItem)
                    .yield(1)
                    .incomplete(false)
                    .build();
            recipe = recipeRepository.save(recipe);

            recipeService.addIngredientComponent(recipe.getId(), ingredient.getId(), 10L, Unit.GRAM);
            
            return recipe.getId();
        });

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // Act
        for (int i = 0; i < threads; i++) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    stockService.depleteStock(venueId, recipeId, 1, UUID.randomUUID());
                    successCount.incrementAndGet();
                } catch (ObjectOptimisticLockingFailureException e) {
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    // It's possible that a transaction rollback wraps the OOLFE
                    if (e.getCause() instanceof ObjectOptimisticLockingFailureException || 
                        e.toString().contains("OptimisticLockingFailureException")) {
                        failureCount.incrementAndGet();
                    } else {
                        System.err.println("Unexpected error: " + e.getClass().getName() + " - " + e.getMessage());
                    }
                }
            }, executor));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();

        // Assert
        System.out.println("Success count: " + successCount.get());
        System.out.println("Failure count: " + failureCount.get());
        
        // At least some should fail if they are truly concurrent
        assertTrue(failureCount.get() > 0, "Expected at least one optimistic locking failure. Success: " + successCount.get() + ", Failure: " + failureCount.get());
    }
}
