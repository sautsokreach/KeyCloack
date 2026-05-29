package com.demo.keycloak.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class KafkaMessageConsumerTest {

    @InjectMocks
    private KafkaMessageConsumer consumer;

    @Test
    void handleUserEvent_processesLoginSuccessfully() {
        assertThatCode(() -> consumer.handleUserEvent(record("alice", "login")))
                .doesNotThrowAnyException();
    }

    @Test
    void handleUserEvent_processesAnyNonFailActionSuccessfully() {
        for (String action : new String[]{"logout", "update-profile", "view-dashboard", "register"}) {
            assertThatCode(() -> consumer.handleUserEvent(record("alice", action)))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void handleUserEvent_throwsRuntimeExceptionForFailAction() {
        assertThatThrownBy(() -> consumer.handleUserEvent(record("alice", "fail")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated failure");
    }

    @Test
    void handleDlt_completesSuccessfullyWithValidEvent() {
        ConsumerRecord<String, UserEvent> record = new ConsumerRecord<>(
                "user-events.DLT", 0, 0L, "alice",
                new UserEvent("alice", "fail", Instant.now()));

        assertThatCode(() -> consumer.handleDlt(record))
                .doesNotThrowAnyException();
    }

    @Test
    void handleDlt_handlesNullValueWithoutException() {
        ConsumerRecord<String, UserEvent> record =
                new ConsumerRecord<>("user-events.DLT", 0, 0L, "alice", null);

        assertThatCode(() -> consumer.handleDlt(record))
                .doesNotThrowAnyException();
    }

    private ConsumerRecord<String, UserEvent> record(String key, String action) {
        return new ConsumerRecord<>("user-events", 0, 0L, key,
                new UserEvent(key, action, Instant.now()));
    }
}
