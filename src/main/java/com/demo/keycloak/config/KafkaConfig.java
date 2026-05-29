package com.demo.keycloak.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.support.converter.RecordMessageConverter;
import org.springframework.kafka.support.converter.StringJsonMessageConverter;

@Configuration
public class KafkaConfig {

    @Value("${kafka.topics.user-events}")
    private String userEventsTopic;

    @Value("${kafka.topics.user-events-dlt}")
    private String userEventsDlt;

    // 3 partitions = 3 consumers can read in parallel (key Kafka concept)
    @Bean
    public NewTopic userEventsTopic() {
        return TopicBuilder.name(userEventsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    // Dead Letter Topic — failed messages land here after retries are exhausted
    @Bean
    public NewTopic userEventsDltTopic() {
        return TopicBuilder.name(userEventsDlt)
                .partitions(1)
                .replicas(1)
                .build();
    }

    // Converts between JSON bytes on the wire and Java objects in listeners
    @Bean
    public RecordMessageConverter kafkaMessageConverter() {
        return new StringJsonMessageConverter();
    }
}
