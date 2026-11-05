package com.aston.configserver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = ConfigServerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.cloud.config.server.native.search-locations=file:../config-repo/",
    "spring.cloud.config.enabled=false"
})
class ConfigServerTest {
    @Autowired TestRestTemplate http;
    @LocalServerPort int port;

    @Test void servesExternalConfigurationForAllClients() {
        for (String name : new String[]{"user-service", "api-gateway", "discovery-server", "notification-service"}) {
            var response = http.getForEntity("/" + name + "/default", String.class);
            assertEquals(200, response.getStatusCode().value());
            assertTrue(response.getBody().contains("external-config-repo"));
            assertTrue(response.getBody().contains("server.port"));
        }
        assertTrue(http.getForObject("/api-gateway/default", String.class).contains("lb://user-service"));
    }

    @Test void notificationDefaultsRemainUtf8() {
        var response = http.getForObject("/notification-service/default", com.fasterxml.jackson.databind.JsonNode.class);
        String site = null;
        for (var source : response.get("propertySources")) {
            if (source.get("source").has("app.site-name")) {
                site = source.get("source").get("app.site-name").asText();
                break;
            }
        }
        assertEquals("${SITE_NAME:ваш сайт}", site);
    }

    @Test void realConfigClientLoadsRemoteProperties() {
        try (var context = new SpringApplicationBuilder(Client.class).properties(
                "spring.config.location=optional:classpath:/client-bootstrap-not-present.yml",
                "spring.application.name=user-service",
                "spring.config.import=configserver:http://localhost:" + port,
                "spring.cloud.config.enabled=true",
                "spring.main.web-application-type=none",
                "spring.jmx.enabled=false").run()) {
            var env = context.getEnvironment();
            assertEquals("external-config-repo", env.getProperty("info.platform.configuration"));
            assertEquals("8082", env.getProperty("server.port"));
            assertEquals("framework", env.getProperty("server.forward-headers-strategy"));
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Client {}
}
