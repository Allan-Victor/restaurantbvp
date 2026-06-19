package com.allan.bvp.restaurant.domain.enums;

/**
 * Defines the possible states of an order in the system.
 */
public enum OrderStatus {
    /** The order is currently being built and has not yet been finalized. */
    OPEN,
    /** The order has been confirmed and items are ready for preparation/service. */
    CONFIRMED,
    /** The order has been cancelled. */
    CANCELLED,
    /** The order has been fully served and paid for. */
    COMPLETED
}
