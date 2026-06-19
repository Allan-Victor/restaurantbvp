package com.allan.bvp.restaurant.domain.model;

import com.allan.bvp.restaurant.domain.enums.ServicePeriod;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

/**
 * Represents an item on the restaurant menu.
 * Each menu item is associated with a specific venue and may have an associated recipe.
 */
@Entity
@Table(name = "MENU_ITEM")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MenuItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "venue_id", nullable = false)
    private String venueId;

    @Column(nullable = false)
    private String name;

    @Column(name = "price_minor", nullable = false)
    private Long priceMinor;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_period")
    private ServicePeriod servicePeriod;

    private boolean active;

    @OneToOne(mappedBy = "menuItem", cascade = CascadeType.ALL)
    private Recipe recipe;
}
