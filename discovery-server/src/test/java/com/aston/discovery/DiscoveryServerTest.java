package com.aston.discovery;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.config.import=file:../config-repo/discovery-server.yml",
    "spring.cloud.config.enabled=false",
    "eureka.client.service-url.defaultZone=http://localhost:1/eureka/"
})
class DiscoveryServerTest {
    @Autowired TestRestTemplate http;
    @Test void registersFindsAndRemovesService() {
        String body = """
          {"instance":{"instanceId":"test-user-1","hostName":"localhost","app":"USER-SERVICE",
          "ipAddr":"127.0.0.1","status":"UP","port":{"$":8082,"@enabled":"true"},
          "vipAddress":"user-service","dataCenterInfo":{"@class":"com.netflix.appinfo.InstanceInfo$DefaultDataCenterInfo","name":"MyOwn"}}}
          """;
        var headers = new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        try {
            assertEquals(204, http.postForEntity("/eureka/apps/USER-SERVICE", new HttpEntity<>(body, headers), String.class).getStatusCode().value());
            var found = http.exchange("/eureka/apps/USER-SERVICE/test-user-1", HttpMethod.GET, new HttpEntity<>(headers), String.class);
            assertEquals(200, found.getStatusCode().value());
            assertTrue(found.getBody().contains("test-user-1"));
        } finally {
            http.delete("/eureka/apps/USER-SERVICE/test-user-1");
        }
        assertEquals(404, http.getForEntity("/eureka/apps/USER-SERVICE/test-user-1", String.class).getStatusCode().value());
    }
}
