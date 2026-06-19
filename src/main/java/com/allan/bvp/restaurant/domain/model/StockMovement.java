package com.allan.bvp.restaurant.domain.model;

import com.allan.bvp.restaurant.domain.enums.MovementType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents a single change in stock level.
 * This entity is part of an append-only ledger, meaning it should never be updated or deleted.
 */
@Entity
@Table(name = "STOCK_MOVEMENT", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"venue_id", "stock_item_id", "idempotency_key"})
})
@Getter
@Setter(AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class StockMovement {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false)
    private UUID id;

    @Column(name = "venue_id", nullable = false, updatable = false)
    private String venueId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_item_id", nullable = false, updatable = false)
    private StockItem stockItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, updatable = false)
    private MovementType type;

    /**
     * The quantity of the movement in minor units.
     * Note: For DEPLETION and WASTAGE, this is usually stored as a positive value and subtracted from on-hand.
     */
    @Column(name = "qty_minor", nullable = false, updatable = false)
    private Long qtyMinor;

    /**
     * A unique key used to ensure that a specific business event (like a receipt or an order item)
     * is only processed once in the stock ledger.
     */
    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    /**
     * Reference to the source event that triggered this movement (e.g., OrderItemId).
     */
    @Column(name = "source_event", updatable = false)
    private UUID sourceEvent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
