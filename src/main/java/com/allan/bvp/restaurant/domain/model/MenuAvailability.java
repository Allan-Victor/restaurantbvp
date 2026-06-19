package com.allan.bvp.restaurant.domain.model;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

/**
 * Represents the availability of a MenuItem based on current stock levels and manual overrides.
 * This is a read-model (projection) in the CQRS architecture.
 */
@Entity
@Table(name = "MENU_AVAILABILITY")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuAvailability {

    @Id
    @Column(name = "menu_item_id")
    private UUID menuItemId;

    /**
     * The associated menu item. The ID of this entity is shared with MenuItem.
     */
    @OneToOne
    @MapsId
    @JoinColumn(name = "menu_item_id")
    private MenuItem menuItem;

    /**
     * The number of portions that can currently be prepared based on on-hand stock.
     */
    @Column(name = "makeable_quantity")
    private long makeableQuantity;

    /**
     * Final availability status (considering both makeable quantity and manual overrides).
     */
    private boolean available;

    /**
     * Flag indicating if a manager has manually overridden the availability.
     */
    @Column(name = "manual_override")
    private boolean manualOverride;

    /**
     * If manual_override is true, this field defines the overridden availability state.
     */
    @Column(name = "override_state")
    private boolean overrideState;

    /**
     * Updates the final availability status.
     * If manual override is active, it uses the override state.
     * Otherwise, the item is available if at least one portion can be made.
     */
    public void updateStatus() {
        if (manualOverride) {
            this.available = overrideState;
        } else {
            this.available = makeableQuantity > 0;
        }
    }
}
