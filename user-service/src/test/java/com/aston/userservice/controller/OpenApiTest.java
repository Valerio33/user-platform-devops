package com.aston.userservice.controller;

import com.aston.userservice.UserServiceApplication;
import com.aston.userservice.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

@SpringBootTest(classes = UserServiceApplication.class, properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration"
})
@AutoConfigureMockMvc
class OpenApiTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserService service;

    @Test
    void documentsCrudDtoHalAndErrors() throws Exception {
        mvc.perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.info.title").value("User Service API"))
            .andExpect(jsonPath("$.paths['/api/users'].get.responses['200']").exists())
            .andExpect(jsonPath("$.paths['/api/users'].post.responses['201'].headers.Location").exists())
            .andExpect(jsonPath("$.paths['/api/users/{id}'].get.responses['404']").exists())
            .andExpect(jsonPath("$.paths['/api/users/{id}'].put.responses['409'].content['application/problem+json']").exists())
            .andExpect(jsonPath("$.paths['/api/users/{id}'].delete.responses['204'].content").doesNotExist())
            .andExpect(jsonPath("$.components.schemas.UserRequest.required", hasItems("name", "email", "age")))
            .andExpect(jsonPath("$.components.schemas.User").doesNotExist())
            .andExpect(jsonPath("$.paths['/api/users'].get.responses['200'].content['application/hal+json']").exists());
    }

    @Test
    void servesSwaggerUiAndConfig() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/swagger-ui/index.html"));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk())
            .andExpect(content().string(containsString("Swagger UI")));
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk())
            .andExpect(jsonPath("$.url").value("/v3/api-docs"));
    }

    @Test
    void specificationContainsCompleteRequestAndResponseSchemas() throws Exception {
        String json = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var document = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/openapi.json"), json);
        checkReferences(document, document);
        var schemas = document.path("components").path("schemas");
        var request = schemas.path("UserRequest").path("properties");
        org.junit.jupiter.api.Assertions.assertEquals(100, request.path("name").path("maxLength").asInt());
        org.junit.jupiter.api.Assertions.assertEquals(1, request.path("name").path("minLength").asInt());
        org.junit.jupiter.api.Assertions.assertEquals(0, request.path("age").path("minimum").asInt(-1));
        org.junit.jupiter.api.Assertions.assertEquals(150, request.path("age").path("maximum").asInt());
        for (String path : java.util.List.of("/api/users", "/api/users/{id}")) {
            var operations = document.path("paths").path(path);
            for (var entry : operations.properties()) {
                var operation = entry.getValue();
                org.junit.jupiter.api.Assertions.assertFalse(operation.path("summary").asText().isBlank());
                org.junit.jupiter.api.Assertions.assertTrue(operation.path("responses").has("503"));
                if (entry.getKey().equals("post") || entry.getKey().equals("put")) {
                    org.junit.jupiter.api.Assertions.assertTrue(operation.path("requestBody").path("required").asBoolean());
                    org.junit.jupiter.api.Assertions.assertEquals("#/components/schemas/UserRequest",
                            operation.at("/requestBody/content/application~1json/schema/$ref").asText());
                }
            }
        }
        org.junit.jupiter.api.Assertions.assertEquals(2, document.path("paths").size());
        var collection = schemas.path("UserCollection").path("properties");
        org.junit.jupiter.api.Assertions.assertTrue(collection.has("_embedded"));
        org.junit.jupiter.api.Assertions.assertTrue(collection.has("_links"));
    }

    private void checkReferences(com.fasterxml.jackson.databind.JsonNode node,
                                 com.fasterxml.jackson.databind.JsonNode root) {
        if (node.isObject() && node.has("$ref")) {
            String ref = node.path("$ref").asText();
            org.junit.jupiter.api.Assertions.assertTrue(ref.startsWith("#/"));
            org.junit.jupiter.api.Assertions.assertFalse(root.at(ref.substring(1)).isMissingNode(), ref);
        }
        if (node.isContainerNode()) node.forEach(child -> checkReferences(child, root));
    }

    @Test
    void swaggerJavascriptIsAvailableForTryItOut() throws Exception {
        mvc.perform(get("/swagger-ui/swagger-ui-bundle.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SwaggerUIBundle")));
        mvc.perform(get("/swagger-ui/swagger-initializer.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/v3/api-docs/swagger-config")));
    }

    @Test
    void linksAndLocationUseGatewayAddress() throws Exception {
        var request = new com.aston.userservice.dto.UserRequest("Иван", "ivan@example.com", 25);
        var response = new com.aston.userservice.dto.UserResponse(1L, "Иван", "ivan@example.com", 25,
                java.time.LocalDateTime.of(2026, 10, 28, 12, 0));
        org.mockito.Mockito.when(service.create(request)).thenReturn(response);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/users")
                .header("X-Forwarded-Host", "localhost:8080")
                .header("X-Forwarded-Proto", "http")
                .header("X-Forwarded-Port", "8080")
                .contentType("application/json")
                .content("{\"name\":\"Иван\",\"email\":\"ivan@example.com\",\"age\":25}"))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", "http://localhost:8080/api/users/1"))
            .andExpect(jsonPath("$._links.self.href").value("http://localhost:8080/api/users/1"))
            .andExpect(jsonPath("$._links.users.href").value("http://localhost:8080/api/users"));
    }
}
