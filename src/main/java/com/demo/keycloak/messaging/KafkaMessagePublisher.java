package com.demo.keycloak.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaMessagePublisher {

    private final KafkaTemplate<String, UserEvent> kafkaTemplate;

    @Value("${kafka.topics.user-events}")
    private String topic;

    /**
     * Publish a user event to Kafka.
     *
     * The message key (username) determines which partition receives the message.
     * All events for the same user always go to the same partition — ordering is
     * preserved per user, which is impossible to guarantee in RabbitMQ.
     */
    public void publishUserEvent(String username, String action) {
        UserEvent event = new UserEvent(username, action, Instant.now());

        // key = username → consistent partition routing for the same user
        CompletableFuture<SendResult<String, UserEvent>> future =
                kafkaTemplate.send(topic, username, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish Kafka event: {}", ex.getMessage());
            } else {
                log.info("Kafka event published → topic={} partition={} offset={} key={}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        username);
            }
        });
    }

    /**
     * Publish to a specific partition by choosing a numeric partition index.
     * Demonstrates explicit partition targeting (not common, but good to understand).
     */
    public void publishToPartition(String username, String action, int partition) {
        UserEvent event = new UserEvent(username, action, Instant.now());

        CompletableFuture<SendResult<String, UserEvent>> future =
                kafkaTemplate.send(topic, partition, username, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish to partition {}: {}", partition, ex.getMessage());
            } else {
                log.info("Published to explicit partition={} offset={}", partition,
                        result.getRecordMetadata().offset());
            }
        });
    }
}
