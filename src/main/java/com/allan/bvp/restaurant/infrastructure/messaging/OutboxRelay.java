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
 * Periodically polls the OUTBOX_EVENT table for undispatched events
 * and publishes them to the Spring ApplicationEventPublisher.
 * This implements the Transactional Outbox pattern relay.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    private final OutboxRepository outboxRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Polls for undispatched events and relays them.
     * Fixed delay is set to 1 second.
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
