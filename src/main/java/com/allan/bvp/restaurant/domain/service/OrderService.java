package com.allan.bvp.restaurant.domain.service;

import com.allan.bvp.restaurant.domain.enums.OrderChannel;
import com.allan.bvp.restaurant.domain.enums.OrderStatus;
import com.allan.bvp.restaurant.domain.model.MenuItem;
import com.allan.bvp.restaurant.domain.model.Order;
import com.allan.bvp.restaurant.domain.model.OrderItem;
import com.allan.bvp.restaurant.domain.model.OutboxEvent;
import com.allan.bvp.restaurant.domain.repository.MenuItemRepository;
import com.allan.bvp.restaurant.domain.repository.OrderRepository;
import com.allan.bvp.restaurant.domain.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for managing customer orders and their lifecycles.
 * Handles order creation, item addition, and the confirmation process including event emission.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final MenuItemRepository menuItemRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new order in the OPEN state.
     *
     * @param venueId the ID of the venue where the order is placed
     * @param channel the channel through which the order originated (e.g., POS)
     * @return the created Order
     */
    @Transactional
    public Order createOrder(String venueId, OrderChannel channel) {
        Order order = Order.builder()
                .venueId(venueId)
                .channel(channel)
                .status(OrderStatus.OPEN)
                .build();
        return orderRepository.save(order);
    }

    /**
     * Adds an item to an existing OPEN order.
     *
     * @param orderId       the ID of the target order
     * @param menuItemId    the ID of the menu item to add
     * @param quantity      the number of items to add
     * @param idempotencyKey a unique key for replay-safety of this specific item addition
     * @return the updated Order
     * @throws IllegalArgumentException if the order is not found or not in OPEN status
     */
    @Transactional
    public Order addItemToOrder(UUID orderId, UUID menuItemId, int quantity, String idempotencyKey) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (order.getStatus() != OrderStatus.OPEN) {
            throw new IllegalStateException("Cannot add items to a " + order.getStatus() + " order");
        }

        MenuItem menuItem = menuItemRepository.findById(menuItemId)
                .orElseThrow(() -> new IllegalArgumentException("MenuItem not found"));

        OrderItem item = OrderItem.builder()
                .order(order)
                .menuItem(menuItem)
                .quantity(quantity)
                .idempotencyKey(idempotencyKey)
                .build();

        order.getItems().add(item);
        return orderRepository.save(order);
    }

    /**
     * Confirms an order, changing its status to CONFIRMED and emitting events for each item.
     * Events are recorded in the transactional outbox to ensure reliable delivery to downstream services.
     *
     * @param orderId the ID of the order to confirm
     * @return the confirmed Order
     * @throws IllegalArgumentException if the order is not found
     */
    @Transactional
    public Order confirmOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        if (order.getStatus() != OrderStatus.OPEN) {
            throw new IllegalStateException("Order is already " + order.getStatus());
        }

        order.setStatus(OrderStatus.CONFIRMED);
        Order saved = orderRepository.save(order);

        for (OrderItem item : saved.getItems()) {
            emitOrderItemConfirmedEvent(item);
        }

        return saved;
    }

    private void emitOrderItemConfirmedEvent(OrderItem item) {
        try {
            OutboxEvent event = OutboxEvent.builder()
                    .venueId(item.getOrder().getVenueId())
                    .eventType("OrderItemConfirmed")
                    .payload(objectMapper.writeValueAsString(item))
                    .build();
            outboxRepository.save(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize OrderItem for outbox", e);
        }
    }
}
