package com.allan.bvp.restaurant.infrastructure.messaging;

import com.allan.bvp.restaurant.domain.model.OutboxEvent;
import com.allan.bvp.restaurant.domain.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The OutboxRelay is a critical component of the Transactional Outbox pattern.
 *
 * <p>Why we use this:</p>
 * When a business operation happens (e.g., an order is placed), we need to do two things:
 * 1. Update the database (Order table).
 * 2. Notify other parts of the system (e.g., Inventory, Kitchen).
 *
 * <p>The Problem:</p>
 * If we update the DB but the notification fails (network issue), the system is inconsistent.
 * If we send the notification but the DB transaction fails, the system is also inconsistent.
 *
 * <p>The Solution (Transactional Outbox):</p>
 * Instead of sending the notification directly, we save the event into an "OUTBOX_EVENT" table
 * within the SAME database transaction as the business operation.
 * This class (OutboxRelay) then periodically polls that table and "relays" the events
 * to their destination (in this case, an in-process event bus).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    private final OutboxRepository outboxRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * This method runs periodically to find events that haven't been dispatched yet.
     * It is scheduled to run every 1 second (fixedDelay = 1000ms).
     *
     * <p>How it works:</p>
     * 1. Fetch undispatched events from the database.
     * 2. For each event, publish it to the Spring ApplicationEventPublisher.
     * 3. If publishing succeeds, mark the event as dispatched=true.
     * 4. If publishing fails, leave it as dispatched=false so it can be retried in the next run.
     */
    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void relayEvents() {
        List<OutboxEvent> events = outboxRepository.findByDispatchedFalseOrderByCreatedAtAsc();
        if (events.isEmpty()) {
            return;
        }

        log.debug("Found {} undispatched outbox events", events.size());
        for (OutboxEvent event : events) {
            try {
                // Publish the event to the in-process bus
                eventPublisher.publishEvent(event);
                
                // Mark as dispatched
                event.setDispatched(true);
                outboxRepository.save(event);
                
                log.debug("Relayed event {} of type {}", event.getId(), event.getEventType());
            } catch (Exception e) {
                log.error("Failed to relay outbox event {}", event.getId(), e);
                // We don't mark as dispatched, so it will be retried
            }
        }
    }
}
