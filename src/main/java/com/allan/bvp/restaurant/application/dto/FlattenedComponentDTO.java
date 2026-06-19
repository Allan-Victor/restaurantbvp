package com.allan.bvp.restaurant.application.dto;

import com.allan.bvp.restaurant.domain.model.Ingredient;
import lombok.Value;

/**
 * Data Transfer Object representing a leaf ingredient and its total required quantity
 * after flattening a multi-level recipe structure.
 */
@Value
public class FlattenedComponentDTO {
    /**
     * The leaf ingredient.
     */
    Ingredient ingredient;

    /**
     * The total quantity of this ingredient required for the recipe in minor units.
     */
    Long totalQtyMinor;
}
