package com.aston.userservice.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aston.userservice.entity.User;
import com.aston.userservice.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class UserApiIT {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired MockMvc mvc;
    @Autowired UserRepository repository;
    @Autowired com.aston.userservice.event.OutboxRepository outbox;
    @Autowired ObjectMapper mapper;

    @BeforeEach
    void clean() {
        outbox.deleteAllInBatch();
        repository.deleteAllInBatch();
    }

    private String body(String name, String email, int age) throws Exception {
        return mapper.writeValueAsString(
                new com.aston.userservice.dto.UserRequest(name, email, age));
    }

    @Test
    void fullCrud() throws Exception {
        String result =
                mvc.perform(
                                post("/api/users")
                                        .contentType(APPLICATION_JSON)
                                        .content(body("Иван", "ivan@example.com", 25)))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        var created = mapper.readTree(result);
        long id = created.get("id").asLong();
        String time = created.get("createdAt").asText();
        assertEquals(1, repository.count());
        mvc.perform(get("/api/users/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ivan@example.com"));
        mvc.perform(get("/api/users")).andExpect(jsonPath("$._embedded.users.length()").value(1));
        mvc.perform(
                        put("/api/users/{id}", id)
                                .contentType(APPLICATION_JSON)
                                .content(body("Пётр", "petr@example.com", 30)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdAt").value(time));
        assertEquals("Пётр", repository.findById(id).orElseThrow().getName());
        mvc.perform(delete("/api/users/{id}", id)).andExpect(status().isNoContent());
        assertEquals(0, repository.count());
        mvc.perform(get("/api/users/{id}", id)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/users/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void duplicateCreateRollsBackAndNextRequestWorks() throws Exception {
        repository.saveAndFlush(new User("Иван", "a@example.com", 25));
        mvc.perform(
                        post("/api/users")
                                .contentType(APPLICATION_JSON)
                                .content(body("Пётр", "a@example.com", 30)))
                .andExpect(status().isConflict());
        assertEquals(1, repository.count());
        mvc.perform(
                        post("/api/users")
                                .contentType(APPLICATION_JSON)
                                .content(body("Пётр", "b@example.com", 30)))
                .andExpect(status().isCreated());
        assertEquals(2, repository.count());
    }

    @Test
    void duplicateUpdateRollsBackEveryField() throws Exception {
        repository.saveAndFlush(new User("Первый", "a@example.com", 25));
        User second = repository.saveAndFlush(new User("Второй", "b@example.com", 30));
        var oldTime = second.getCreatedAt();
        long id = second.getId();
        mvc.perform(
                        put("/api/users/{id}", id)
                                .contentType(APPLICATION_JSON)
                                .content(body("Изменён", "a@example.com", 40)))
                .andExpect(status().isConflict());
        User unchanged = repository.findById(id).orElseThrow();
        assertEquals("Второй", unchanged.getName());
        assertEquals("b@example.com", unchanged.getEmail());
        assertEquals(30, unchanged.getAge());
        assertEquals(oldTime, unchanged.getCreatedAt());
        mvc.perform(
                        put("/api/users/{id}", id)
                                .contentType(APPLICATION_JSON)
                                .content(body("Изменён", "c@example.com", 40)))
                .andExpect(status().isOk());
    }

    @Test
    void invalidRequestDoesNotPersist() throws Exception {
        mvc.perform(post("/api/users").contentType(APPLICATION_JSON).content(body(" ", "bad", -1)))
                .andExpect(status().isBadRequest());
        assertEquals(0, repository.count());
    }

    @Test
    void invalidAndMissingIds() throws Exception {
        mvc.perform(get("/api/users/0")).andExpect(status().isBadRequest());
        mvc.perform(
                        put("/api/users/9223372036854775807")
                                .contentType(APPLICATION_JSON)
                                .content(body("Иван", "a@example.com", 25)))
                .andExpect(status().isNotFound());
        assertEquals(0, repository.count());
    }
}
