package com.allan.bvp.restaurant.domain.enums;

/**
 * Defines the types of stock movements that can occur in the inventory ledger.
 */
public enum MovementType {
    /** Stock received from a supplier. */
    RECEIPT,
    /** Stock consumed during recipe preparation. */
    DEPLETION,
    /** Stock discarded due to spoilage or damage. */
    WASTAGE,
    /** Manual adjustment of stock levels after a physical count. */
    ADJUSTMENT
}
