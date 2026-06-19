package com.allan.bvp.restaurant.domain.repository;

import com.allan.bvp.restaurant.domain.model.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Repository interface for managing {@link ProcessedEvent} persistence.
 *
 * <p>Junior Tip:</p>
 * This is the "Data Access Object" (DAO) for our "Done" list.
 * It helps us quickly check if we've already handled a specific message.
 */
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {
    /**
     * Checks if an event has already been successfully processed by a specific service.
     *
     * @param eventId      the ID of the event
     * @param consumerName the name of the service (e.g., "KitchenService")
     * @return true if we should skip this event, false if we should process it.
     */
    boolean existsByEventIdAndConsumerName(UUID eventId, String consumerName);
}
