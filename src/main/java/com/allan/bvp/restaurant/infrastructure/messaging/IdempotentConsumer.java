package com.allan.bvp.restaurant.infrastructure.messaging;

import com.allan.bvp.restaurant.domain.model.ProcessedEvent;
import com.allan.bvp.restaurant.domain.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Utility service to ensure idempotent processing of events.
 * It tracks processed event IDs per consumer in the database.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotentConsumer {

    private final ProcessedEventRepository processedEventRepository;

    /**
     * Executes the given action exactly once per eventId and consumerName.
     * The action and the tracking of completion are performed within the same transaction.
     *
     * @param eventId      the unique ID of the event
     * @param consumerName the unique name of the consumer
     * @param action       the action to perform
     */
    @Transactional
    public void consume(UUID eventId, String consumerName, Consumer<UUID> action) {
        if (processedEventRepository.existsByEventIdAndConsumerName(eventId, consumerName)) {
            log.debug("Event {} already processed by consumer {}, skipping", eventId, consumerName);
            return;
        }

        log.debug("Processing event {} with consumer {}", eventId, consumerName);
        action.accept(eventId);

        ProcessedEvent processedEvent = ProcessedEvent.builder()
                .eventId(eventId)
                .consumerName(consumerName)
                .build();
        processedEventRepository.save(processedEvent);
    }
}
