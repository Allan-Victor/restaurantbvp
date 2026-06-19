package com.allan.bvp.restaurant.domain.repository;

import com.allan.bvp.restaurant.domain.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for managing {@link OutboxEvent} persistence.
 *
 * <p>Junior Tip:</p>
 * This is the "Data Access Object" (DAO) for our Outbox "To-Do" list.
 * Spring Data JPA automatically provides common methods like {@code save()} and {@code findById()}.
 */
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    /**
     * Finds all events that haven't been sent yet.
     * We order by creation time to ensure we send events in the order they happened.
     *
     * @return a list of undispatched outbox events
     */
    List<OutboxEvent> findByDispatchedFalseOrderByCreatedAtAsc();
}
