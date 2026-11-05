package com.aston.notificationservice;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfiguration {
    @Bean
    org.springframework.kafka.core.ConsumerFactory<String, NotificationRequest> consumerFactory(
            org.springframework.boot.autoconfigure.kafka.KafkaProperties properties) {
        var json = new org.springframework.kafka.support.serializer.JsonDeserializer<>(NotificationRequest.class, false);
        return new org.springframework.kafka.core.DefaultKafkaConsumerFactory<>(properties.buildConsumerProperties(),
            new org.apache.kafka.common.serialization.StringDeserializer(),
            new org.springframework.kafka.support.serializer.ErrorHandlingDeserializer<>(json));
    }
    @Bean
    org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory<String, NotificationRequest> kafkaListenerContainerFactory(
            org.springframework.kafka.core.ConsumerFactory<String, NotificationRequest> consumerFactory,
            DefaultErrorHandler errorHandler) {
        var factory = new org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory<String, NotificationRequest>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(errorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        return factory;
    }
    // DLT must preserve malformed JSON as bytes and serialize valid typed events.
    @Bean
    org.springframework.kafka.core.ProducerFactory<String, Object> kafkaProducerFactory(
            org.springframework.boot.autoconfigure.kafka.KafkaProperties properties) {
        var serializers = new java.util.LinkedHashMap<Class<?>, org.apache.kafka.common.serialization.Serializer<?>>();
        serializers.put(byte[].class, new org.apache.kafka.common.serialization.ByteArraySerializer());
        serializers.put(String.class, new org.apache.kafka.common.serialization.StringSerializer());
        serializers.put(NotificationRequest.class, new org.springframework.kafka.support.serializer.JsonSerializer<NotificationRequest>());
        return new org.springframework.kafka.core.DefaultKafkaProducerFactory<>(properties.buildProducerProperties(),
            new org.apache.kafka.common.serialization.StringSerializer(),
            new org.springframework.kafka.support.serializer.DelegatingByTypeSerializer(serializers));
    }

    @Bean
    KafkaTemplate<String, Object> kafkaTemplate(org.springframework.kafka.core.ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
    @Bean
    NewTopic events(@Value("${app.kafka.topic}") String topic) {
        return TopicBuilder.name(topic).partitions(1).replicas(1).build();
    }
    @Bean
    NewTopic deadLetters(@Value("${app.kafka.topic}") String topic) {
        return TopicBuilder.name(topic + ".DLT").partitions(1).replicas(1).build();
    }
    @Bean
    DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, exception) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 3L));
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }
}
