package com.allan.bvp.restaurant.domain.service;

import com.allan.bvp.restaurant.domain.enums.OrderChannel;
import com.allan.bvp.restaurant.domain.enums.OrderStatus;
import com.allan.bvp.restaurant.domain.model.MenuItem;
import com.allan.bvp.restaurant.domain.model.Order;
import com.allan.bvp.restaurant.domain.model.OrderItem;
import com.allan.bvp.restaurant.domain.repository.MenuItemRepository;
import com.allan.bvp.restaurant.domain.repository.OrderRepository;
import com.allan.bvp.restaurant.domain.repository.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OrderService}.
 */
@ExtendWith(MockitoExtension.class)
public class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OrderService orderService;

    @Test
    void createOrder_ShouldCreateOpenOrder() {
        // Arrange
        String venueId = "VENUE-1";
        OrderChannel channel = OrderChannel.POS;

        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        Order order = orderService.createOrder(venueId, channel);

        // Assert
        assertNotNull(order);
        assertEquals(venueId, order.getVenueId());
        assertEquals(channel, order.getChannel());
        assertEquals(OrderStatus.OPEN, order.getStatus());
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void addItemToOrder_ShouldAddOrderItem() {
        // Arrange
        UUID orderId = UUID.randomUUID();
        UUID menuItemId = UUID.randomUUID();
        Order order = Order.builder().id(orderId).status(OrderStatus.OPEN).items(new ArrayList<>()).build();
        MenuItem menuItem = MenuItem.builder().id(menuItemId).name("Burger").build();

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(menuItemRepository.findById(menuItemId)).thenReturn(Optional.of(menuItem));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        Order updatedOrder = orderService.addItemToOrder(orderId, menuItemId, 2, "KEY-1");

        // Assert
        assertEquals(1, updatedOrder.getItems().size());
        OrderItem item = updatedOrder.getItems().get(0);
        assertEquals(menuItem, item.getMenuItem());
        assertEquals(2, item.getQuantity());
        assertEquals("KEY-1", item.getIdempotencyKey());
    }

    @Test
    void confirmOrder_ShouldEmitEventsAndChangeStatus() throws Exception {
        // Arrange
        UUID orderId = UUID.randomUUID();
        Order order = Order.builder()
                .id(orderId)
                .venueId("V1")
                .status(OrderStatus.OPEN)
                .items(new ArrayList<>())
                .build();
        
        MenuItem item = MenuItem.builder().id(UUID.randomUUID()).name("Pizza").build();
        OrderItem orderItem = OrderItem.builder().order(order).menuItem(item).quantity(1).idempotencyKey("K1").build();
        order.getItems().add(orderItem);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        // Act
        Order confirmedOrder = orderService.confirmOrder(orderId);

        // Assert
        assertEquals(OrderStatus.CONFIRMED, confirmedOrder.getStatus());
        verify(outboxRepository, times(1)).save(any());
        verify(orderRepository).save(order);
    }
}
