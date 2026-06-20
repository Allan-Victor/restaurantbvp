package com.allan.bvp.restaurant.infrastructure.messaging;

import com.allan.bvp.restaurant.domain.model.OutboxEvent;
import com.allan.bvp.restaurant.domain.repository.OutboxRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
    private KafkaProducerService kafkaProducerService;

    @InjectMocks
    private OutboxRelay outboxRelay;

    @Test
    void relayEvents_ShouldPublishToKafkaAndMarkAsDispatched() {
        // Arrange
        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .venueId("VENUE-1")
                .eventType("TestEvent")
                .payload("{}")
                .dispatched(false)
                .build();

        when(outboxRepository.findByDispatchedFalseOrderByCreatedAtAsc()).thenReturn(List.of(event));
        when(kafkaProducerService.sendEvent(event)).thenReturn(CompletableFuture.completedFuture(null));

        // Act
        outboxRelay.relayEvents();

        // Assert
        verify(kafkaProducerService).sendEvent(event);
        assertTrue(event.isDispatched());
        verify(outboxRepository).save(event);
    }

    @Test
    void relayEvents_ShouldNotMarkAsDispatchedIfKafkaPublishFails() {
        // Arrange
        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .venueId("VENUE-1")
                .eventType("TestEvent")
                .payload("{}")
                .dispatched(false)
                .build();

        when(outboxRepository.findByDispatchedFalseOrderByCreatedAtAsc()).thenReturn(List.of(event));
        CompletableFuture<SendResult<String, String>> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("Kafka error"));
        when(kafkaProducerService.sendEvent(event)).thenReturn(future);

        // Act
        outboxRelay.relayEvents();

        // Assert
        verify(kafkaProducerService).sendEvent(event);
        assertTrue(!event.isDispatched());
        verify(outboxRepository, never()).save(event);
    }
}
