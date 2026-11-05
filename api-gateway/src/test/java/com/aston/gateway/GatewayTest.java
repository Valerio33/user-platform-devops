package com.aston.gateway;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.config.import=file:../config-repo/api-gateway.yml",
    "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
    "spring.cloud.loadbalancer.cache.enabled=false",
    "resilience4j.circuitbreaker.instances.userService.minimum-number-of-calls=3",
    "resilience4j.circuitbreaker.instances.userService.sliding-window-size=3",
    "resilience4j.circuitbreaker.instances.userService.wait-duration-in-open-state=60s",
    "resilience4j.circuitbreaker.instances.userService.automatic-transition-from-open-to-half-open-enabled=false",
    "resilience4j.timelimiter.instances.userService.timeout-duration=1s"
})
class GatewayTest {
    static final AtomicInteger status = new AtomicInteger(200);
    static final AtomicInteger hits = new AtomicInteger();
    static volatile boolean slow;
    static final DisposableServer backend = HttpServer.create().port(0).handle((request, response) -> {
        hits.incrementAndGet();
        return response.status(status.get()).header("Content-Type", "text/plain")
                .sendString(Mono.just(request.method().name() + " " + request.uri() + " " + request.requestHeaders().get("Host"))
                        .delayElement(slow ? Duration.ofMillis(3500) : Duration.ZERO));
    }).bindNow();

    @DynamicPropertySource
    static void backend(DynamicPropertyRegistry properties) {
        properties.add("spring.cloud.discovery.client.simple.instances.notification-service[0].uri",
                () -> "http://localhost:" + backend.port());
        properties.add("spring.cloud.discovery.client.simple.instances.user-service[0].uri",
                () -> "http://localhost:" + backend.port());
    }
    @LocalServerPort int port;
    @Autowired CircuitBreakerRegistry registry;
    WebTestClient client;

    @BeforeEach
    void reset() {
        status.set(200); hits.set(0); slow = false;
        registry.circuitBreaker("userService").reset();
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(10)).build();
    }
    @AfterAll static void close() { backend.disposeNow(); }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/app.js", "/styles.css", "/api/users", "/swagger-ui.html", "/swagger-ui/index.html", "/v3/api-docs", "/v3/api-docs/swagger-config"})
    void routesByServiceNameAndPreservesPublicHost(String path) {
        client.get().uri(path).exchange().expectStatus().isOk().expectBody(String.class)
                .isEqualTo("GET " + path + " localhost:" + port);
    }

    @Test
    void notificationPostRoutesByServiceName() {
        client.post().uri("/api/notifications").header("Content-Type", "application/json")
                .bodyValue("{\"operation\":\"CREATED\",\"email\":\"a@example.test\"}")
                .exchange().expectStatus().isOk().expectBody(String.class)
                .value(body -> assertTrue(body.startsWith("POST /api/notifications ")));
        assertEquals(1, hits.get());
    }

    @Test
    void notificationRouteAllowsSmtpToFinishBeyondGlobalTimeout() {
        slow = true;
        client.post().uri("/api/notifications").header("Content-Type", "application/json")
                .bodyValue("{\"operation\":\"CREATED\",\"email\":\"a@example.test\"}")
                .exchange().expectStatus().isOk();
        assertEquals(1, hits.get(), "Notification POST must not be retried");
    }

    @Test
    void clientErrorsDoNotOpenCircuit() {
        for (int code : new int[] {400, 404, 409, 400, 404}) {
            status.set(code);
            client.get().uri("/api/users/1").exchange().expectStatus().isEqualTo(code);
        }
        assertEquals(CircuitBreaker.State.CLOSED, registry.circuitBreaker("userService").getState());
    }

    @Test
    void serverFailuresOpenCircuitAndRecoveryClosesIt() {
        status.set(503);
        for (int i=0; i<3; i++) expectFallback();
        var breaker = registry.circuitBreaker("userService");
        assertEquals(CircuitBreaker.State.OPEN, breaker.getState());
        int before = hits.get();
        expectFallback();
        assertEquals(before, hits.get(), "Open circuit must not call backend");
        status.set(200);
        breaker.transitionToHalfOpenState();
        for (int i=0; i<2; i++) client.get().uri("/api/users").exchange().expectStatus().isOk();
        assertEquals(CircuitBreaker.State.CLOSED, breaker.getState());
    }

    @Test
    void slowBackendReturns503() {
        slow = true;
        expectFallback();
    }

    @Test
    void failedWritesAreNotRetriedAutomatically() {
        status.set(500);
        client.post().uri("/api/users").header("Content-Type", "application/json")
                .bodyValue("{\"name\":\"Иван\",\"email\":\"a@example.com\",\"age\":25}")
                .exchange().expectStatus().isEqualTo(503).expectBody().jsonPath("$.status").isEqualTo(503);
        assertEquals(1, hits.get());
    }

    private void expectFallback() {
        client.get().uri("/api/users").exchange().expectStatus().isEqualTo(503)
                .expectHeader().contentTypeCompatibleWith("application/problem+json")
                .expectBody().jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.title").isEqualTo("User service unavailable");
    }
}
