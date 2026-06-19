package com.allan.bvp.restaurant.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks events that have been successfully processed by a specific consumer.
 *
 * <p>Junior Tip:</p>
 * This is like a "Done" list. When a service (like Inventory) finishes processing
 * an event, it writes the Event ID here. If the same event comes again, the service
 * checks this table, sees it's already there, and says "I've already done this, I'll skip it."
 * This prevents doing the same work (like charging a customer) twice.
 */
@Entity
@Table(name = "PROCESSED_EVENT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The ID of the event that was processed.
     */
    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    /**
     * The name of the consumer that processed the event (e.g., "InventoryService").
     * This allows multiple DIFFERENT services to process the SAME event exactly once each.
     */
    @Column(name = "consumer_name", nullable = false)
    private String consumerName;

    @CreationTimestamp
    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;
}
