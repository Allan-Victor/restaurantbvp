package com.allan.bvp.restaurant.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

/**
 * Event payload for menu item availability changes.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuItemAvailabilityEvent {
    private UUID menuItemId;
    private String venueId;
    private boolean available;
    private long makeableQuantity;
}
