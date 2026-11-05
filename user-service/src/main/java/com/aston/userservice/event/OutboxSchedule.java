package com.aston.userservice.event;

import org.slf4j.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxSchedule {
    private static final Logger log = LoggerFactory.getLogger(OutboxSchedule.class);
    private final OutboxPublisher publisher;
    public OutboxSchedule(OutboxPublisher publisher) { this.publisher = publisher; }
    @Scheduled(fixedDelayString = "${app.outbox.delay-ms:2000}")
    public void publish() {
        try {
            for (int i = 0; i < 100 && publisher.publishNext(); i++) {
            }
        } catch (RuntimeException ex) {
            log.warn("Событие осталось в outbox; отправка будет повторена: {}", ex.getMessage());
        }
    }
}
