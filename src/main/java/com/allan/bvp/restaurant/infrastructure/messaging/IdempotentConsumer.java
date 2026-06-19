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
 * The IdempotentConsumer ensures that an event is processed exactly once,
 * even if it is received multiple times.
 *
 * <p>Why we use this:</p>
 * In distributed systems (or even with an Outbox relay), an event might be delivered more than once.
 * For example, if the {@link OutboxRelay} sends an event but fails to mark it as dispatched due to a crash,
 * it will send it again when it restarts.
 *
 * <p>The Problem:</p>
 * If processing an event has side effects (like decreasing stock twice), multiple deliveries
 * can lead to incorrect data.
 *
 * <p>The Solution (Idempotent Consumer):</p>
 * We keep track of which events have already been successfully processed by each "consumer".
 * Before processing an event, we check a database table ("PROCESSED_EVENT") to see if we've seen it before.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotentConsumer {

    private final ProcessedEventRepository processedEventRepository;

    /**
     * Executes the given business action exactly once per eventId and consumerName.
     * Both the action and the record of completion happen within the same database transaction.
     *
     * <p>How to use it in a junior-friendly way:</p>
     * <pre>
     * idempotentConsumer.consume(event.getId(), "InventoryService", (id) -> {
     *     // Your business logic here (e.g., update stock)
     * });
     * </pre>
     *
     * @param eventId      The unique ID of the event being processed.
     * @param consumerName The name of the service/component processing the event.
     * @param action       The lambda expression or method containing the business logic.
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
