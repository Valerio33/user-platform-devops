package com.aston.userservice.event;

import com.aston.userservice.dto.UserRequest;
import com.aston.userservice.service.UserService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties="app.outbox.enabled=false")
@Testcontainers
class OutboxStoreIT {
    @Container static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");
    @DynamicPropertySource static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager manager;
    @Autowired UserService users;
    TransactionTemplate tx;
    OutboxStore store;
    @BeforeEach void reset() {
        tx = new TransactionTemplate(manager);
        store = new OutboxStore(jdbc);
        jdbc.execute("TRUNCATE notification_outbox, users RESTART IDENTITY CASCADE");
    }
    private OutboxStore.Claim claim() { return tx.execute(s -> store.claim().orElseThrow()); }
    private void insert(String email) {
        jdbc.update("INSERT INTO notification_outbox(operation,email) VALUES ('CREATED',?)",email);
    }
    @Test void leaseAllowsOtherRecipientsButBlocksSameEmail() {
        insert("a@example.test"); insert("a@example.test"); insert("b@example.test");
        var first=claim(); var second=claim();
        assertEquals("a@example.test",first.event().email());
        assertEquals("b@example.test",second.event().email());
        assertTrue(tx.execute(s -> store.claim()).isEmpty());
        tx.executeWithoutResult(s -> store.complete(first));
        assertEquals("a@example.test",claim().event().email());
    }
    @Test void staleCallbackCannotDeleteNewLease() {
        insert("a@example.test"); var old=claim();
        jdbc.update("UPDATE notification_outbox SET locked_until=CURRENT_TIMESTAMP-INTERVAL '1 second'");
        var fresh=claim();
        tx.executeWithoutResult(s -> store.complete(old));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM notification_outbox", Integer.class));
        tx.executeWithoutResult(s -> store.complete(fresh));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM notification_outbox", Integer.class));
    }
    @Test void lockedRowDoesNotBlockAnotherRecipient() throws Exception {
        insert("a@example.test"); insert("b@example.test");
        try (var connection = java.sql.DriverManager.getConnection(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword())) {
            connection.setAutoCommit(false);
            connection.createStatement().executeQuery("SELECT * FROM notification_outbox WHERE id=1 FOR UPDATE");
            assertEquals("b@example.test", claim().event().email());
            connection.rollback();
        }
    }
    @Test void rollbackRemovesUserAndEventTogether() {
        tx.executeWithoutResult(status -> {
            users.create(new UserRequest("Rollback", "rollback@example.test", 25));
            status.setRollbackOnly();
        });
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM users",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM notification_outbox",Integer.class));
    }
    @Test void createAndDeleteProduceEventsWithCorrectEmail() {
        var user=users.create(new UserRequest("Event", "event@example.test", 25));
        users.delete(user.id());
        var events=jdbc.queryForList("SELECT operation FROM notification_outbox ORDER BY id",String.class);
        assertEquals(java.util.List.of("CREATED","DELETED"),events);
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM notification_outbox WHERE email='event@example.test'",Integer.class));
    }
}
