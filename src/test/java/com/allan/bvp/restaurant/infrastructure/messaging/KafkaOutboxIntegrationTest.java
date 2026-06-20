package com.allan.bvp.restaurant.infrastructure.messaging;

import com.allan.bvp.restaurant.domain.enums.OrderChannel;
import com.allan.bvp.restaurant.domain.model.Order;
import com.allan.bvp.restaurant.domain.model.OutboxEvent;
import com.allan.bvp.restaurant.domain.repository.OutboxRepository;
import com.allan.bvp.restaurant.domain.service.OrderService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for the Kafka Outbox Relay (KAF-2).
 * Verifies that events saved in the outbox are eventually published to Kafka.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public class KafkaOutboxIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private TestKafkaConsumer testKafkaConsumer;

    //@Disabled("Testcontainers requires a working Docker environment which is not available.")
    @Test
    void confirmOrder_ShouldPublishEventToKafka() {
        // Arrange
        String venueId = "TEST-VENUE";
        Order order = orderService.createOrder(venueId, OrderChannel.POS);
        orderService.confirmOrder(order.getId());

        // Assert outbox has events
        List<OutboxEvent> events = outboxRepository.findAll();
        assertFalse(events.isEmpty(), "Outbox should contain events");

        // Wait for Relay to pick up and Kafka to receive (KAF-2)
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertTrue(testKafkaConsumer.getReceivedPayloads().size() >= 1, "Kafka should have received events");
        });
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        public TestKafkaConsumer testKafkaConsumer() {
            return new TestKafkaConsumer();
        }
    }

    static class TestKafkaConsumer {
        private final List<String> receivedPayloads = new CopyOnWriteArrayList<>();

        @KafkaListener(topics = "ordering.events", groupId = "test-group")
        public void consume(String payload) {
            receivedPayloads.add(payload);
        }

        public List<String> getReceivedPayloads() {
            return receivedPayloads;
        }
    }
}
