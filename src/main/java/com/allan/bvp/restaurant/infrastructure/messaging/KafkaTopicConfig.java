package com.allan.bvp.restaurant.infrastructure.messaging;

import org.springframework.kafka.retrytopic.RetryTopicConfiguration;
import org.springframework.kafka.retrytopic.RetryTopicConfigurationBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.beans.factory.annotation.Value;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration class for Kafka topics and retry policies.
 *
 * <p>Junior Developer Tip:</p>
 * This class ensures that the required topics exist in Kafka before the application starts.
 * We follow the topology defined in EPIC 6 (KAF-1).
 * It also configures Dead Letter Topics (DLT) for failed events (KAF-4).
 */
@Configuration
public class KafkaTopicConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, String> producerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public NewTopic inventoryEvents() {
        return TopicBuilder.name("inventory.events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic availabilityEvents() {
        return TopicBuilder.name("availability.events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderingEvents() {
        return TopicBuilder.name("ordering.events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic menuEvents() {
        return TopicBuilder.name("menu.events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Configures retry logic and DLT for all topics (KAF-4).
     * If an event fails to be processed after 3 attempts, it goes to <topic>.DLT.
     */
    @Bean
    public RetryTopicConfiguration retryTopicConfig(KafkaTemplate<String, String> template) {
        return RetryTopicConfigurationBuilder
                .newInstance()
                .maxAttempts(3)
                .fixedBackOff(1000)
                .dltSuffix(".DLT")
                .create(template);
    }
}
