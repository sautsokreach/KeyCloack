package com.demo.keycloak.messaging;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class KafkaMessageConsumer {

    /**
     * @RetryableTopic tells Spring Kafka to automatically retry failed messages
     * using separate retry topics, then send to DLT after all attempts fail.
     *
     * RabbitMQ equivalent: dead-letter exchange after x-death count.
     * Kafka difference: retries happen via separate topics, not re-queuing,
     * so the original topic is never blocked.
     */
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 3000, multiplier = 2),
            dltTopicSuffix = ".DLT",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            autoCreateTopics = "false"
    )
    @KafkaListener(
            topics = "${kafka.topics.user-events}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void handleUserEvent(ConsumerRecord<String, UserEvent> record) {
        UserEvent event = record.value();

        // ConsumerRecord gives you metadata RabbitMQ doesn't expose this cleanly:
        // partition (which shard), offset (position in the log — can replay from here)
        log.info("Kafka received → topic={} partition={} offset={} key={}",
                record.topic(), record.partition(), record.offset(), record.key());
        log.info("  event: user={} action={} at={}",
                event.getUsername(), event.getAction(), event.getTimestamp());

        // Simulate a processing error so you can watch the retry + DLT flow
        if ("fail".equals(event.getAction())) {
            throw new RuntimeException("Simulated failure for action=fail — retries will follow");
        }

        log.info("  Processed successfully.");
    }

    /**
     * DLT handler — called when all retries are exhausted.
     * RabbitMQ equivalent: consuming from the dead-letter queue.
     */
    @DltHandler
    public void handleDlt(ConsumerRecord<String, UserEvent> record) {
        log.warn("DLT received (all retries exhausted) → partition={} offset={} key={}",
                record.partition(), record.offset(), record.key());
        if (record.value() != null) {
            log.warn("  DLT event: user={} action={}",
                    record.value().getUsername(), record.value().getAction());
        }
        // In production: persist to DB, alert, or route to manual review
    }
}
