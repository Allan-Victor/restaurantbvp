package com.allan.bvp.restaurant.domain.event;

import com.allan.bvp.restaurant.domain.model.StockItem;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Internal event published when stock levels change.
 * Used for triggering read-model updates within the same process.
 */
@Getter
@AllArgsConstructor
public class StockLevelChangedInternalEvent {
    private final StockItem stockItem;
}
