package com.allan.bvp.restaurant.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents an event stored in the transactional outbox.
 *
 * <p>Junior Tip:</p>
 * This is essentially a "To-Do" list for the system. When we save an Order, we also
 * save an {@code OutboxEvent} here saying "Hey, an order was created".
 * Later, the {@link com.allan.bvp\restaurant\infrastructure\messaging\OutboxRelay} will
 * check this table and send the message out.
 */
@Entity
@Table(name = "OUTBOX_EVENT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The venueId used for Kafka partitioning.
     */
    @Column(name = "venue_id", nullable = false)
    private String venueId;

    /**
     * The type of event (e.g., "OrderItemConfirmed").
     * Used by consumers to know how to handle the payload.
     */
    @Column(name = "event_type", nullable = false)
    private String eventType;

    /**
     * The actual data of the event, stored as a JSON string.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Flag to track if the event has been sent out by the Relay.
     * false = pending, true = sent.
     */
    private boolean dispatched;
}
