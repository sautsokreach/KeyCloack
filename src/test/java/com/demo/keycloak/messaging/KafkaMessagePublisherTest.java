package com.demo.keycloak.messaging;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KafkaMessagePublisherTest {

    @Mock
    private KafkaTemplate<String, UserEvent> kafkaTemplate;

    @InjectMocks
    private KafkaMessagePublisher publisher;

    private static final String TOPIC = "user-events";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(publisher, "topic", TOPIC);
    }

    @Test
    void publishUserEvent_sendsWithUsernameAsKey() {
        when(kafkaTemplate.send(eq(TOPIC), eq("alice"), any(UserEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(buildSendResult(0, 0L)));

        publisher.publishUserEvent("alice", "login");

        ArgumentCaptor<UserEvent> captor = ArgumentCaptor.forClass(UserEvent.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq("alice"), captor.capture());

        UserEvent captured = captor.getValue();
        assertThat(captured.getUsername()).isEqualTo("alice");
        assertThat(captured.getAction()).isEqualTo("login");
        assertThat(captured.getTimestamp()).isNotNull();
    }

    @Test
    void publishUserEvent_doesNotThrowOnBrokerFailure() {
        CompletableFuture<SendResult<String, UserEvent>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Broker unavailable"));
        when(kafkaTemplate.send(eq(TOPIC), anyString(), any(UserEvent.class))).thenReturn(failed);

        assertThatCode(() -> publisher.publishUserEvent("alice", "login"))
                .doesNotThrowAnyException();
    }

    @Test
    void publishToPartition_sendsToSpecifiedPartition() {
        when(kafkaTemplate.send(eq(TOPIC), eq(1), eq("bob"), any(UserEvent.class)))
                .thenReturn(CompletableFuture.completedFuture(buildSendResult(1, 5L)));

        publisher.publishToPartition("bob", "logout", 1);

        ArgumentCaptor<UserEvent> captor = ArgumentCaptor.forClass(UserEvent.class);
        verify(kafkaTemplate).send(eq(TOPIC), eq(1), eq("bob"), captor.capture());

        assertThat(captor.getValue().getUsername()).isEqualTo("bob");
        assertThat(captor.getValue().getAction()).isEqualTo("logout");
    }

    @Test
    void publishToPartition_doesNotThrowOnBrokerFailure() {
        CompletableFuture<SendResult<String, UserEvent>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("Broker unavailable"));
        when(kafkaTemplate.send(eq(TOPIC), anyInt(), anyString(), any(UserEvent.class))).thenReturn(failed);

        assertThatCode(() -> publisher.publishToPartition("alice", "fail", 0))
                .doesNotThrowAnyException();
    }

    private SendResult<String, UserEvent> buildSendResult(int partition, long offset) {
        ProducerRecord<String, UserEvent> record = new ProducerRecord<>(TOPIC, "key", null);
        RecordMetadata meta = new RecordMetadata(
                new TopicPartition(TOPIC, partition), offset, 0, 0L, 0, 0);
        return new SendResult<>(record, meta);
    }
}
