package com.allan.bvp.restaurant.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Tracks events that have been successfully processed by a specific consumer.
 * This is used to implement the Idempotent Consumer pattern.
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
     * The name of the consumer that processed the event.
     * This allows multiple consumers to process the same event exactly once each.
     */
    @Column(name = "consumer_name", nullable = false)
    private String consumerName;

    @CreationTimestamp
    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;
}
