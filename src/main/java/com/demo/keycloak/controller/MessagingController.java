package com.demo.keycloak.controller;

import com.demo.keycloak.messaging.MessagePublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessagingController {

    private final MessagePublisher publisher;
    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${rabbitmq.routing-key}")
    private String routingKey;

    @Value("${rabbitmq.dead-letter-queue}")
    private String dlqQueue;

    /**
     * Publish a custom event for the authenticated user.
     * Example: POST /api/messages/send  {"action": "custom-action"}
     */
    @PostMapping("/send")
    public Map<String, String> sendMessage(@RequestBody Map<String, String> body,
                                           Authentication authentication) {
        Jwt jwt = (Jwt) authentication.getPrincipal();
        String username = jwt.getClaimAsString("preferred_username");
        String action = body.getOrDefault("action", "manual-trigger");

        publisher.publishUserEvent(username, action);
        return Map.of("status", "sent", "user", username, "action", action);
    }

    /** Test endpoint: sends any JSON body directly to the queue — use to trigger consumer deserialization errors. */
    @PostMapping("/send-raw")
    public Map<String, String> sendRaw(@RequestBody Map<String, Object> body,
                                       Authentication authentication) {
        publisher.publishRaw(body);
        return Map.of("status", "sent-raw", "payload", body.toString());
    }

    /** Drain the DLQ and republish all messages back to the main queue for reprocessing. */
    @PostMapping("/retry-dlq")
    public Map<String, Object> retryDlq() {
        int count = 0;
        Message message;
        while ((message = rabbitTemplate.receive(dlqQueue)) != null) {
            rabbitTemplate.send(exchange, routingKey, message);
            count++;
        }
        return Map.of("status", "republished", "count", count);
    }
}
