package com.aston.userservice.controller;

import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aston.userservice.dto.*;
import com.aston.userservice.exception.UserNotFoundException;
import com.aston.userservice.service.UserService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

@org.springframework.context.annotation.Import(UserModelAssembler.class)
@WebMvcTest(UserController.class)
class UserControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserService service;
    private final UserRequest request = new UserRequest("Иван", "ivan@example.com", 25);
    private final UserResponse response =
            new UserResponse(
                    1L, "Иван", "ivan@example.com", 25, LocalDateTime.of(2026, 1, 1, 12, 0));
    private final String json = "{\"name\":\"Иван\",\"email\":\"ivan@example.com\",\"age\":25}";

    @Test
    void createsWithLocationAndDto() throws Exception {
        when(service.create(request)).thenReturn(response);
        mvc.perform(post("/api/users").contentType(APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/users/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Иван"))
                .andExpect(jsonPath("$.email").value("ivan@example.com"))
                .andExpect(jsonPath("$.age").value(25))
                .andExpect(jsonPath("$.createdAt").value("2026-01-01T12:00:00"));
        verify(service).create(request);
    }

    @Test
    void readsUser() throws Exception {
        when(service.getById(1L)).thenReturn(response);
        mvc.perform(get("/api/users/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$._links.self.href").value("http://localhost/api/users/1"))
                .andExpect(jsonPath("$._links.users.href").value("http://localhost/api/users"));
    }

    @Test
    void readsList() throws Exception {
        when(service.getAll()).thenReturn(List.of(response));
        mvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.users[0].email").value("ivan@example.com"));
    }

    @Test
    void emptyListHasHalCollection() throws Exception {
        when(service.getAll()).thenReturn(List.of());
        mvc.perform(get("/api/users")).andExpect(status().isOk()).andExpect(jsonPath("$._embedded.users").isEmpty());
    }

    @Test
    void updatesAndReturnsDto() throws Exception {
        when(service.update(1L, request)).thenReturn(response);
        mvc.perform(put("/api/users/1").contentType(APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$._links.self.href").value("http://localhost/api/users/1"))
                .andExpect(jsonPath("$._links.users.href").value("http://localhost/api/users"));
        verify(service).update(1L, request);
    }

    @Test
    void deletesWithoutBody() throws Exception {
        mvc.perform(delete("/api/users/1"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        verify(service).delete(1L);
    }

    @Test
    void missingGetIs404() throws Exception {
        when(service.getById(9L)).thenThrow(new UserNotFoundException(9L));
        mvc.perform(get("/api/users/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void missingUpdateIs404() throws Exception {
        when(service.update(9L, request)).thenThrow(new UserNotFoundException(9L));
        mvc.perform(put("/api/users/9").contentType(APPLICATION_JSON).content(json))
                .andExpect(status().isNotFound());
    }

    @Test
    void missingDeleteIs404() throws Exception {
        doThrow(new UserNotFoundException(9L)).when(service).delete(9L);
        mvc.perform(delete("/api/users/9")).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"name\": \" \", \"email\": \"a@b.ru\", \"age\": 25}",
                "{\"name\":\"Иван\",\"email\":\"bad\",\"age\":25}",
                "{\"name\":\"Иван\",\"email\":\"a@b.ru\",\"age\":-1}",
                "{\"name\":\"Иван\",\"email\":\"a@b.ru\",\"age\":151}",
                "{\"name\":\"Иван\",\"email\":\"a@b.ru\",\"age\":null}"
            })
    void validatesPostAndPut(String body) throws Exception {
        mvc.perform(post("/api/users").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isMap());
        mvc.perform(put("/api/users/1").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{broken",
                "{\"id\":1}",
                "{\"name\":\"Иван\",\"email\":\"a@b.ru\",\"age\":1.5}"
            })
    void rejectsInvalidJson(String body) throws Exception {
        mvc.perform(post("/api/users").contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsNonNumericId() throws Exception {
        mvc.perform(get("/api/users/abc")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void duplicateCreateIs409() throws Exception {
        when(service.create(request))
                .thenThrow(
                        new DataIntegrityViolationException(
                                "sql", new SQLException("duplicate", "23505")));
        mvc.perform(post("/api/users").contentType(APPLICATION_JSON).content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Пользователь с таким email уже существует"));
    }

    @Test
    void duplicateUpdateIs409() throws Exception {
        when(service.update(1L, request))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        mvc.perform(put("/api/users/1").contentType(APPLICATION_JSON).content(json))
                .andExpect(status().isConflict());
    }
    @Test
    void unavailableDatabaseReturns503() throws Exception {
        when(service.getAll()).thenThrow(
                new org.springframework.transaction.CannotCreateTransactionException("connection failed"));
        mvc.perform(get("/api/users"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("База данных временно недоступна"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/hal+json", "application/json"})
    void collectionLinksAreNavigable(String mediaType) throws Exception {
        when(service.getAll()).thenReturn(List.of(response));
        when(service.getById(1L)).thenReturn(response);
        var result = mvc.perform(get("/api/users").accept(mediaType))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.self.href").value("http://localhost/api/users"))
                .andExpect(jsonPath("$._embedded.users[0]._links.update.href").value("http://localhost/api/users/1"))
                .andExpect(jsonPath("$._embedded.users[0]._links.delete.href").value("http://localhost/api/users/1"))
                .andReturn();
        var body = new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString());
        String self = body.at("/_embedded/users/0/_links/self/href").asText();
        mvc.perform(get(java.net.URI.create(self)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void deletionOffersCollectionLink() throws Exception {
        mvc.perform(delete("/api/users/1"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Link", "<http://localhost/api/users>;rel=\"users\""))
                .andExpect(content().string(""));
    }

    @Test
    void followsLinksFromCreationThroughUpdateAndDeletion() throws Exception {
        when(service.create(request)).thenReturn(response);
        when(service.getById(1L)).thenReturn(response);
        when(service.getAll()).thenReturn(List.of(response));
        when(service.update(1L, request)).thenReturn(response);
        var created = mvc.perform(post("/api/users").contentType(APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn().getResponse();
        var links = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(created.getContentAsString()).path("_links");
        org.junit.jupiter.api.Assertions.assertEquals(created.getHeader("Location"), links.at("/self/href").asText());
        mvc.perform(get(java.net.URI.create(links.at("/self/href").asText())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
        mvc.perform(get(java.net.URI.create(links.at("/users/href").asText())))
                .andExpect(status().isOk()).andExpect(jsonPath("$._embedded.users[0].id").value(1));
        mvc.perform(put(java.net.URI.create(links.at("/update/href").asText()))
                .contentType(APPLICATION_JSON).content(json))
                .andExpect(status().isOk()).andExpect(jsonPath("$._links.self").exists());
        mvc.perform(delete(java.net.URI.create(links.at("/delete/href").asText())))
                .andExpect(status().isNoContent());
        verify(service).delete(1L);
    }
}
