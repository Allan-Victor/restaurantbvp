package com.allan.bvp.restaurant.domain.model;

import com.allan.bvp.restaurant.domain.enums.Unit;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

/**
 * Represents a single component within a recipe.
 * A component can be either a raw ingredient or a sub-recipe.
 */
@Entity
@Table(name = "RECIPE_COMPONENT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecipeComponent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipe_id", nullable = false)
    private Recipe recipe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ingredient_id")
    private Ingredient ingredient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_recipe_id")
    private Recipe subRecipe;

    @Column(name = "required_qty_minor", nullable = false)
    private Long requiredQtyMinor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Unit unit;
}
