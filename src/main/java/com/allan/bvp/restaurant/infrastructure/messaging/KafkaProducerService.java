package com.allan.bvp.restaurant.infrastructure.messaging;

import com.allan.bvp.restaurant.domain.model.OutboxEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for producing events to Kafka.
 *
 * <p>Junior Developer Tip:</p>
 * This service is like a post office. It takes an event and knows which Kafka "topic" (like a mailbox)
 * to send it to based on the event type. It also ensures that events for the same venue
 * are sent to the same partition by using the {@code venueId} as the partition key.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Mapping of event types to Kafka topics.
     * Following the topology defined in EPIC 6.
     */
    private static final Map<String, String> TOPIC_MAP = Map.of(
            "StockLevelChanged", "inventory.events",
            "ParLevelBreached", "inventory.events",
            "MenuItemAvailable", "availability.events",
            "MenuItemUnavailable", "availability.events",
            "OrderItemConfirmed", "ordering.events",
            "MenuItemPublished", "menu.events"
    );

    /**
     * Sends an outbox event to Kafka.
     * Uses venueId as the partition key to ensure order preservation per venue (KAF-1).
     *
     * @param event The outbox event to send.
     * @return A CompletableFuture that completes when the message is acknowledged by Kafka.
     */
    public CompletableFuture<SendResult<String, String>> sendEvent(OutboxEvent event) {
        String topic = TOPIC_MAP.getOrDefault(event.getEventType(), "default.events");
        String key = event.getVenueId(); // Partition by venueId (KAF-1)

        log.debug("Sending event {} to topic {} with key {}", event.getId(), topic, key);

        return kafkaTemplate.send(topic, key, event.getPayload())
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.debug("Successfully sent event {} to Kafka", event.getId());
                    } else {
                        log.error("Failed to send event {} to Kafka", event.getId(), ex);
                    }
                });
    }
}
