package com.aston.userservice.event;

import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name="app.outbox.enabled", havingValue="true", matchIfMissing=true)
public class OutboxStore {
    private final JdbcTemplate jdbc;
    public OutboxStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public record Claim(long id, String token, UserEvent event) {}

    @Transactional
    public Optional<Claim> claim() {
        String token = UUID.randomUUID().toString();
        return jdbc.query("""
            WITH candidate AS (
                SELECT o.id FROM notification_outbox o
                WHERE (o.locked_until IS NULL OR o.locked_until < CURRENT_TIMESTAMP)
                  AND NOT EXISTS (SELECT 1 FROM notification_outbox older
                      WHERE older.email = o.email AND older.id < o.id)
                ORDER BY o.id LIMIT 1 FOR UPDATE OF o SKIP LOCKED
            )
            UPDATE notification_outbox o
            SET claim_token = ?, locked_until = CURRENT_TIMESTAMP + INTERVAL '120 seconds'
            FROM candidate c WHERE o.id = c.id
            RETURNING o.id, o.operation, o.email
            """, (rs, row) -> new Claim(rs.getLong("id"), token,
                new UserEvent(UserEvent.Operation.valueOf(rs.getString("operation")), rs.getString("email"))), token)
            .stream().findFirst();
    }
    @Transactional
    public void complete(Claim claim) {
        jdbc.update("DELETE FROM notification_outbox WHERE id = ? AND claim_token = ?", claim.id(), claim.token());
    }
    @Transactional
    public void retry(Claim claim) {
        jdbc.update("""
            UPDATE notification_outbox SET claim_token = NULL,
                locked_until = CURRENT_TIMESTAMP + INTERVAL '5 seconds'
            WHERE id = ? AND claim_token = ?
            """, claim.id(), claim.token());
    }
}
