package com.aston.userservice.event;

import jakarta.persistence.*;

@Entity
@Table(name = "notification_outbox")
public class OutboxEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16)
    private UserEvent.Operation operation;
    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "claim_token")
    private String claimToken;
    @Column(name = "locked_until")
    private java.time.Instant lockedUntil;

    protected OutboxEvent() {}
    public OutboxEvent(UserEvent.Operation operation, String email) {
        this.operation = operation;
        this.email = email;
    }
    public Long getId() { return id; }
    public UserEvent toEvent() { return new UserEvent(operation, email); }
}
