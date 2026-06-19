package com.allan.bvp.restaurant.domain.repository;

import com.allan.bvp.restaurant.domain.model.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository interface for managing {@link ProcessedEvent} persistence.
 */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
    /**
     * Checks if an event has already been processed by a specific consumer.
     *
     * @param eventId      the ID of the event
     * @param consumerName the name of the consumer
     * @return true if the event was already processed, false otherwise
     */
    boolean existsByEventIdAndConsumerName(UUID eventId, String consumerName);
}
