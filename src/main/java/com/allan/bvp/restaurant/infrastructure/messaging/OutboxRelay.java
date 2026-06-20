package com.allan.bvp.restaurant.infrastructure.messaging;

import com.allan.bvp.restaurant.domain.model.OutboxEvent;
import com.allan.bvp.restaurant.domain.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final KafkaProducerService kafkaProducerService;

    /**
     * This method runs periodically to find events that haven't been dispatched yet.
     * It is scheduled to run every 1 second (fixedDelay = 1000ms).
     *
     * <p>Junior Developer Tip:</p>
     * This is the "Relay" part of the Outbox pattern (KAF-2). It reads undispatched
     * rows from the DB and publishes them to Kafka. We mark them as dispatched ONLY
     * after Kafka acknowledges receipt.
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
                // Publish to Kafka and wait for acknowledgment (KAF-2)
                kafkaProducerService.sendEvent(event).join();
                
                // Mark as dispatched
                event.setDispatched(true);
                outboxRepository.save(event);
                
                log.debug("Relayed event {} to Kafka", event.getId());
            } catch (Exception e) {
                log.error("Failed to relay outbox event {} to Kafka", event.getId(), e);
                // We don't mark as dispatched, so it will be retried in the next run
            }
        }
    }
}
