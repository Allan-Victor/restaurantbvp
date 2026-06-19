package com.allan.bvp.restaurant.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.List;
import java.util.UUID;

/**
 * Represents the current inventory state of an ingredient at a specific venue.
 * This entity maintains a cached projection of the stock on hand and the weighted-average cost.
 */
@Entity
@Table(name = "STOCK_ITEM")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "venue_id", nullable = false)
    private String venueId;

    @OneToOne
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    /**
     * The current quantity on hand in minor units (e.g., grams or pieces * 1000).
     */
    @Column(name = "on_hand_minor", nullable = false)
    private Long onHandMinor;

    /**
     * The weighted-average cost per minor unit of this stock item.
     * Updated automatically during goods receipt.
     */
    @Column(name = "weighted_average_cost_minor", nullable = false)
    private Long weightedAverageCostMinor;

    /**
     * The threshold level for triggering low-stock alerts.
     */
    @Column(name = "par_level_minor", nullable = false)
    private Long parLevelMinor;

    /**
     * Version field for optimistic locking to prevent lost updates during concurrent modifications.
     */
    @Version
    private int version;

    @OneToMany(mappedBy = "stockItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StockMovement> movements;
}
