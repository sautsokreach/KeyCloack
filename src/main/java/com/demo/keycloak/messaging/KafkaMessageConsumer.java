package com.demo.keycloak.messaging;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

@Slf4j
@Component
public class KafkaMessageConsumer {

    private static final int MAX_STORED = 100;

    private final Deque<Map<String, Object>> receivedMessages = new ConcurrentLinkedDeque<>();
    private final Deque<Map<String, Object>> deadLetterMessages = new ConcurrentLinkedDeque<>();

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

        log.info("Kafka received → topic={} partition={} offset={} key={}",
                record.topic(), record.partition(), record.offset(), record.key());
        log.info("  event: user={} action={} at={}",
                event.getUsername(), event.getAction(), event.getTimestamp());

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("topic", record.topic());
        entry.put("partition", record.partition());
        entry.put("offset", record.offset());
        entry.put("key", record.key());
        entry.put("username", event.getUsername());
        entry.put("action", event.getAction());
        entry.put("eventTimestamp", event.getTimestamp());
        entry.put("receivedAt", Instant.now().toString());
        entry.put("status", "fail".equals(event.getAction()) ? "FAILED" : "OK");

        receivedMessages.addFirst(entry);
        if (receivedMessages.size() > MAX_STORED) receivedMessages.pollLast();

        if ("fail".equals(event.getAction())) {
            throw new RuntimeException("Simulated failure for action=fail — retries will follow");
        }

        log.info("  Processed successfully.");
    }

    @DltHandler
    public void handleDlt(ConsumerRecord<String, UserEvent> record) {
        log.warn("DLT received (all retries exhausted) → partition={} offset={} key={}",
                record.partition(), record.offset(), record.key());

        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("topic", record.topic());
        entry.put("partition", record.partition());
        entry.put("offset", record.offset());
        entry.put("key", record.key());
        entry.put("receivedAt", Instant.now().toString());

        if (record.value() != null) {
            log.warn("  DLT event: user={} action={}",
                    record.value().getUsername(), record.value().getAction());
            entry.put("username", record.value().getUsername());
            entry.put("action", record.value().getAction());
            entry.put("eventTimestamp", record.value().getTimestamp());
        }

        deadLetterMessages.addFirst(entry);
        if (deadLetterMessages.size() > MAX_STORED) deadLetterMessages.pollLast();
    }

    public List<Map<String, Object>> getReceivedMessages() {
        return new ArrayList<>(receivedMessages);
    }

    public List<Map<String, Object>> getDeadLetterMessages() {
        return new ArrayList<>(deadLetterMessages);
    }
}
