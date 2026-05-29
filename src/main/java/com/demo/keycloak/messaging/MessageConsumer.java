package com.demo.keycloak.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MessageConsumer {

    @RabbitListener(queues = "${rabbitmq.queue}")
    public void handleUserEvent(UserEvent event) {
        log.info("Received user event: user={} action={} at={}", event.getUsername(), event.getAction(), event.getTimestamp());
        throw new RuntimeException("Simulated processing error for: " + event.getAction());
    }
}
