package com.aston.userservice.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@ConditionalOnProperty(name="app.outbox.enabled", havingValue="true", matchIfMissing=true)
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxStore store;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper mapper;
    private final String topic;
    public OutboxPublisher(OutboxStore store, KafkaTemplate<String, String> kafka,
            ObjectMapper mapper, @Value("${app.kafka.topic}") String topic) {
        this.store=store; this.kafka=kafka; this.mapper=mapper; this.topic=topic;
    }
    public boolean publishNext() {
        var pending=store.claim();
        if (pending.isEmpty()) return false;
        var claim=pending.get();
        try {
            kafka.send(topic, claim.event().email(), mapper.writeValueAsString(claim.event()))
                .whenComplete((result, failure) -> finish(claim, failure));
        } catch (Exception failure) { finish(claim, failure); }
        return true;
    }
    private void finish(OutboxStore.Claim claim, Throwable failure) {
        try {
            if (failure == null) store.complete(claim);
            else {
                log.warn("Kafka did not confirm outbox event {}", claim.id(), failure);
                store.retry(claim);
            }
        } catch (RuntimeException ex) {
            // Lease expiry recovers a crashed worker or a failed DB callback.
            log.error("Outbox callback failed for {}; lease will expire", claim.id(), ex);
        }
    }
}
