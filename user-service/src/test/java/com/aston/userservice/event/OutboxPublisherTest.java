package com.aston.userservice.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {
    @Mock OutboxStore store;
    @Mock KafkaTemplate<String, String> kafka;
    OutboxPublisher publisher;
    final OutboxStore.Claim claim = new OutboxStore.Claim(1, "token", new UserEvent(UserEvent.Operation.CREATED, "a@example.test"));
    @BeforeEach void setup() { publisher = new OutboxPublisher(store, kafka, new ObjectMapper(), "events"); }
    @Test void emptyDoesNotSend() {
        when(store.claim()).thenReturn(Optional.empty());
        assertFalse(publisher.publishNext());
        verifyNoInteractions(kafka);
    }
    @Test void returnsBeforeAcknowledgementAndDeletesOnlyOnSuccess() {
        when(store.claim()).thenReturn(Optional.of(claim));
        var future = new CompletableFuture<SendResult<String, String>>();
        when(kafka.send(eq("events"), eq("a@example.test"), anyString())).thenReturn(future);
        assertTrue(publisher.publishNext());
        verify(store, never()).complete(any());
        future.complete(null);
        verify(store).complete(claim);
        verify(store, never()).retry(any());
    }
    @Test void failedAcknowledgementSchedulesRetry() {
        when(store.claim()).thenReturn(Optional.of(claim));
        var future = new CompletableFuture<SendResult<String, String>>();
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(future);
        publisher.publishNext();
        future.completeExceptionally(new IllegalStateException("broker unavailable"));
        verify(store).retry(claim);
        verify(store, never()).complete(any());
    }
    @Test void synchronousSendFailureAlsoSchedulesRetry() {
        when(store.claim()).thenReturn(Optional.of(claim));
        when(kafka.send(anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("metadata timeout"));
        assertTrue(publisher.publishNext());
        verify(store).retry(claim);
        verify(store, never()).complete(any());
    }
}
