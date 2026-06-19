package com.allan.bvp.restaurant.domain.enums;

/**
 * Defines the channels through which an order can be placed.
 */
public enum OrderChannel {
    /** Point of Sale - orders placed directly at the venue. */
    POS,
    /** Orders placed via an online platform. */
    ONLINE,
    /** Orders placed via a mobile application. */
    MOBILE
}
