package com.demo.keycloak.messaging;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EmbeddedKafka(
        partitions = 3,
        topics = {"user-events", "user-events.DLT"}
)
@TestPropertySource(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}"
})
@DirtiesContext
class KafkaIntegrationTest {

    @Autowired
    private KafkaMessagePublisher publisher;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Test
    void publishUserEvent_messageArrivesWithCorrectKeyAndPayload() {
        Consumer<String, UserEvent> consumer = createTestConsumer("it-group-publish");
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, "user-events");
        consumer.poll(Duration.ofMillis(200)); // anchor offset at current end

        publisher.publishUserEvent("alice", "login");

        ConsumerRecords<String, UserEvent> records =
                KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
        consumer.close();

        ConsumerRecord<String, UserEvent> found = StreamSupport.stream(records.spliterator(), false)
                .filter(r -> "alice".equals(r.key()) && "login".equals(r.value().getAction()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected message for alice/login not found"));

        assertThat(found.value().getUsername()).isEqualTo("alice");
        assertThat(found.value().getTimestamp()).isNotNull();
    }

    @Test
    void publishToPartition_messageArrivesOnSpecifiedPartition() {
        Consumer<String, UserEvent> consumer = createTestConsumer("it-group-partition");
        embeddedKafkaBroker.consumeFromEmbeddedTopics(consumer, "user-events");
        consumer.poll(Duration.ofMillis(200));

        publisher.publishToPartition("bob", "logout", 1);

        ConsumerRecords<String, UserEvent> records =
                KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(10));
        consumer.close();

        boolean found = StreamSupport.stream(records.spliterator(), false)
                .anyMatch(r -> r.partition() == 1
                        && "bob".equals(r.key())
                        && "logout".equals(r.value().getAction()));

        assertThat(found).as("Expected message on partition 1 for bob/logout").isTrue();
    }

    private Consumer<String, UserEvent> createTestConsumer(String groupId) {
        Map<String, Object> props = KafkaTestUtils.consumerProps(groupId, "true", embeddedKafkaBroker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");

        JsonDeserializer<UserEvent> valueDeserializer = new JsonDeserializer<>(UserEvent.class);
        valueDeserializer.addTrustedPackages("*");

        return new DefaultKafkaConsumerFactory<>(
                props, new StringDeserializer(), valueDeserializer).createConsumer();
    }
}
