package com.allan.bvp.restaurant.domain.model;

import com.allan.bvp.restaurant.domain.enums.Allergen;
import jakarta.persistence.*;
import lombok.*;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a recipe which defines how a MenuItem is prepared.
 * A recipe consists of multiple components (ingredients or sub-recipes) and
 * tracks rolled-up costs and allergens.
 */
@Entity
@Table(name = "RECIPE")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Recipe {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @Column(name = "yield_qty", nullable = false)
    private int yield;

    @Column(name = "rolled_up_cost_minor")
    private Long rolledUpCostMinor;

    private boolean incomplete;

    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<RecipeComponent> components;

    @ElementCollection(targetClass = Allergen.class)
    @CollectionTable(name = "RECIPE_ALLERGEN", joinColumns = @JoinColumn(name = "recipe_id"))
    @Column(name = "allergen")
    @Enumerated(EnumType.STRING)
    private Set<Allergen> allergens;
}
