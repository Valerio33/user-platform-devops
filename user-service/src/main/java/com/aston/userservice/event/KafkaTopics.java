package com.aston.userservice.event;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name="app.outbox.enabled", havingValue="true", matchIfMissing=true)
@Configuration
public class KafkaTopics {
    @Bean
    NewTopic userEvents(@Value("${app.kafka.topic}") String topic) {
        return TopicBuilder.name(topic).partitions(1).replicas(1).build();
    }
}
