package com.demo.keycloak.controller;

import com.demo.keycloak.messaging.KafkaMessagePublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/kafka")
@RequiredArgsConstructor
public class KafkaController {

    private final KafkaMessagePublisher publisher;

    @Value("${kafka.topics.user-events}")
    private String topic;

    /**
     * POST /api/kafka/send
     * Body: {"action": "login"}
     *
     * Message key = username → always routed to the same partition.
     * Compare with RabbitMQ /api/messages/send which uses a routing key on the exchange.
     */
    @PostMapping("/send")
    public Map<String, String> send(@RequestBody Map<String, String> body,
                                    Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String username = jwt.getClaimAsString("preferred_username");
        String action = body.getOrDefault("action", "manual-trigger");

        publisher.publishUserEvent(username, action);
        return Map.of("status", "sent", "topic", topic, "key", username, "action", action);
    }

    /**
     * POST /api/kafka/send?action=fail
     * Shortcut to trigger the retry + DLT flow.
     * The consumer throws on action="fail", then Spring retries 3×, then sends to DLT.
     */
    @PostMapping("/send-fail")
    public Map<String, String> sendFail(Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String username = jwt.getClaimAsString("preferred_username");

        publisher.publishUserEvent(username, "fail");
        return Map.of("status", "sent", "note", "consumer will fail → watch retry topics → DLT");
    }

    /**
     * POST /api/kafka/send-batch
     * Body: [{"action":"a"},{"action":"b"},...]
     * Shows Kafka's strength: high-throughput sequential writes to the same topic.
     */
    @PostMapping("/send-batch")
    public Map<String, Object> sendBatch(@RequestBody List<Map<String, String>> events,
                                         Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String username = jwt.getClaimAsString("preferred_username");

        events.forEach(e -> publisher.publishUserEvent(username, e.getOrDefault("action", "batch")));
        return Map.of("status", "sent", "count", events.size(), "key", username);
    }

    /**
     * POST /api/kafka/send-partition
     * Body: {"action":"test","partition":0}
     * Demonstrates publishing to a specific partition explicitly.
     */
    @PostMapping("/send-partition")
    public Map<String, String> sendPartition(@RequestBody Map<String, String> body,
                                             Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String username = jwt.getClaimAsString("preferred_username");
        String action = body.getOrDefault("action", "partition-test");
        int partition = Integer.parseInt(body.getOrDefault("partition", "0"));

        publisher.publishToPartition(username, action, partition);
        return Map.of("status", "sent", "partition", String.valueOf(partition), "key", username);
    }

    /**
     * GET /api/kafka/info
     * Returns topic/config info to understand the setup.
     */
    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "topic", topic,
                "partitions", 3,
                "consumerGroup", "demo-group",
                "concept", Map.of(
                        "key-routing", "same key → same partition → ordered delivery per user",
                        "offset", "position in partition log — consumers track where they left off",
                        "consumer-group", "each partition is consumed by exactly one member of the group",
                        "dlt", "dead letter topic receives messages after all retries fail",
                        "vs-rabbitmq", "Kafka retains messages after consumption (replay possible); RabbitMQ deletes after ack"
                )
        );
    }
}
