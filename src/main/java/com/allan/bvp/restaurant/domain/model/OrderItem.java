package com.allan.bvp.restaurant.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Represents a single item within an order.
 * Tracks the specific MenuItem, quantity, and provides an idempotency key for replay-safety.
 */
@Entity
@Table(name = "ORDER_ITEM")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "menu_item_id", nullable = false)
    private MenuItem menuItem;

    @Column(nullable = false)
    private int quantity;

    /**
     * A unique key used to ensure this specific item is processed exactly once
     * by downstream systems (like inventory depletion).
     */
    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;
}
