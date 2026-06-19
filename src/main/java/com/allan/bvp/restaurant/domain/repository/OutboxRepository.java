package com.allan.bvp.restaurant.domain.repository;

import com.allan.bvp.restaurant.domain.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for managing {@link OutboxEvent} persistence.
 */
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    /**
     * Finds all events that haven't been dispatched yet, ordered by creation time.
     *
     * @return a list of undispatched outbox events
     */
    List<OutboxEvent> findByDispatchedFalseOrderByCreatedAtAsc();
}
