package com.allan.bvp.restaurant.domain.model;

import com.allan.bvp.restaurant.domain.enums.Allergen;
import com.allan.bvp.restaurant.domain.enums.Unit;
import jakarta.persistence.*;
import lombok.*;

import java.util.Set;
import java.util.UUID;

/**
 * Represents a raw ingredient used in recipes.
 * Tracks base unit, cost, and allergens.
 */
@Entity
@Table(name = "INGREDIENT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ingredient {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "venue_id", nullable = false)
    private String venueId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "base_unit", nullable = false)
    private Unit baseUnit;

    /**
     * The unit cost in minor units (e.g., cents or thousandths of a unit).
     * Typically synchronized with the Weighted Average Cost (WAC) from inventory receipts.
     */
    @Column(name = "unit_cost_minor", nullable = false)
    private Long unitCostMinor;

    @ElementCollection(targetClass = Allergen.class)
    @CollectionTable(name = "INGREDIENT_ALLERGEN", joinColumns = @JoinColumn(name = "ingredient_id"))
    @Column(name = "allergen")
    @Enumerated(EnumType.STRING)
    private Set<Allergen> allergens;

    @OneToOne(mappedBy = "ingredient", cascade = CascadeType.ALL)
    private StockItem stockItem;
}
