package com.aston.userservice.event;
import org.springframework.data.jpa.repository.JpaRepository;
public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {}
