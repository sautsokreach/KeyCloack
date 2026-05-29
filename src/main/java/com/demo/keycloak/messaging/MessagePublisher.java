package com.demo.keycloak.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessagePublisher {

    private final RabbitTemplate rabbitTemplate;

    @Value("${rabbitmq.exchange}")
    private String exchange;

    @Value("${rabbitmq.routing-key}")
    private String routingKey;

    public void publishUserEvent(String username, String action) {
        UserEvent event = new UserEvent(username, action, Instant.now());
        rabbitTemplate.convertAndSend(exchange, routingKey, event);
        log.info("Published user event: {} - {}", username, action);
    }
}
