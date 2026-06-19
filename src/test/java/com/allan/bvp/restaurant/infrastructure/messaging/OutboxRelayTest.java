package com.allan.bvp.restaurant.infrastructure.messaging;

import com.allan.bvp.restaurant.domain.model.OutboxEvent;
import com.allan.bvp.restaurant.domain.repository.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OutboxRelay}.
 */
@ExtendWith(MockitoExtension.class)
public class OutboxRelayTest {

    @Mock
    private OutboxRepository outboxRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private OutboxRelay outboxRelay;

    @Test
    void relayEvents_ShouldPublishAndMarkAsDispatched() {
        // Arrange
        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .eventType("TestEvent")
                .payload("{}")
                .dispatched(false)
                .build();

        when(outboxRepository.findByDispatchedFalseOrderByCreatedAtAsc()).thenReturn(List.of(event));

        // Act
        outboxRelay.relayEvents();

        // Assert
        verify(eventPublisher).publishEvent(event);
        assertTrue(event.isDispatched());
        verify(outboxRepository).save(event);
    }

    @Test
    void relayEvents_ShouldNotMarkAsDispatchedIfPublishFails() {
        // Arrange
        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .eventType("TestEvent")
                .payload("{}")
                .dispatched(false)
                .build();

        when(outboxRepository.findByDispatchedFalseOrderByCreatedAtAsc()).thenReturn(List.of(event));
        doThrow(new RuntimeException("Fail")).when(eventPublisher).publishEvent(any());

        // Act
        outboxRelay.relayEvents();

        // Assert
        verify(eventPublisher).publishEvent(event);
        verify(outboxRepository, never()).save(event);
    }
}
