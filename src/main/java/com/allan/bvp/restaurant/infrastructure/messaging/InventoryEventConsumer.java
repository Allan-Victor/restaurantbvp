package com.allan.bvp.restaurant.infrastructure.messaging;

import com.allan.bvp.restaurant.domain.model.OrderItem;
import com.allan.bvp.restaurant.domain.service.StockService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Consumer for inventory-related events.
 *
 * <p>Junior Developer Tip:</p>
 * This is the "Consumer" part of the Inventory service (T-15). It listens to the
 * "ordering.events" topic. When an order is confirmed, it calls the StockService
 * to deplete stock. It uses IdempotentConsumer to ensure it doesn't process the same
 * order item twice (KAF-3).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryEventConsumer {

    private final StockService stockService;
    private final IdempotentConsumer idempotentConsumer;
    private final ObjectMapper objectMapper;

    /**
     * Listens for order item confirmations and triggers stock depletion.
     *
     * @param payload The JSON payload of the event.
     * @param venueId The venue ID extracted from Kafka message key for partition routing.
     */
    @KafkaListener(topics = "ordering.events", groupId = "inventory-service-group")
    public void onOrderItemConfirmed(String payload, 
                                    @Header(KafkaHeaders.RECEIVED_KEY) String venueId) {
        try {
            OrderItem item = objectMapper.readValue(payload, OrderItem.class);
            UUID eventId = item.getId(); // Using OrderItem ID as the business key for idempotency (KAF-3)

            idempotentConsumer.consume(eventId, "InventoryService", (id) -> {
                log.info("Processing stock depletion for order item {} at venue {}", item.getId(), venueId);
                stockService.depleteStock(venueId, item.getMenuItem().getRecipe().getId(), item.getQuantity(), item.getId());
            });
        } catch (Exception e) {
            log.error("Failed to process order item confirmation for inventory", e);
            throw new RuntimeException(e); // Trigger retry/DLT
        }
    }
}
