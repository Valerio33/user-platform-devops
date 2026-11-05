package com.aston.notificationservice;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
@Component
public class NotificationListener {
    private final NotificationService service;
    public NotificationListener(NotificationService service) { this.service=service; }
    @KafkaListener(topics="${app.kafka.topic}", groupId="${spring.kafka.consumer.group-id}")
    public void receive(NotificationRequest request) { service.send(request); }
}
