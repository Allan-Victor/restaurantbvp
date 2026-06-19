package com.allan.bvp.restaurant.infrastructure.messaging;

import com.allan.bvp.restaurant.domain.model.ProcessedEvent;
import com.allan.bvp.restaurant.domain.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link IdempotentConsumer}.
 */
@ExtendWith(MockitoExtension.class)
public class IdempotentConsumerTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private IdempotentConsumer idempotentConsumer;

    @Test
    void consume_ShouldExecuteActionAndSaveProcessedEvent() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        String consumerName = "TestConsumer";
        Consumer<UUID> action = mock(Consumer.class);

        when(processedEventRepository.existsByEventIdAndConsumerName(eventId, consumerName)).thenReturn(false);

        // Act
        idempotentConsumer.consume(eventId, consumerName, action);

        // Assert
        verify(action).accept(eventId);
        verify(processedEventRepository).save(any(ProcessedEvent.class));
    }

    @Test
    void consume_ShouldSkipIfAlreadyProcessed() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        String consumerName = "TestConsumer";
        Consumer<UUID> action = mock(Consumer.class);

        when(processedEventRepository.existsByEventIdAndConsumerName(eventId, consumerName)).thenReturn(true);

        // Act
        idempotentConsumer.consume(eventId, consumerName, action);

        // Assert
        verify(action, never()).accept(any());
        verify(processedEventRepository, never()).save(any());
    }

    @Test
    void consume_ShouldNotSaveIfActionFails() {
        // Arrange
        UUID eventId = UUID.randomUUID();
        String consumerName = "TestConsumer";
        Consumer<UUID> action = event -> { throw new RuntimeException("Fail"); };

        when(processedEventRepository.existsByEventIdAndConsumerName(eventId, consumerName)).thenReturn(false);

        // Act & Assert
        try {
            idempotentConsumer.consume(eventId, consumerName, action);
        } catch (RuntimeException e) {
            // expected
        }

        verify(processedEventRepository, never()).save(any());
    }
}
