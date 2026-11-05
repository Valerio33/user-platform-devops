package com.aston.userservice.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aston.userservice.dto.UserRequest;
import com.aston.userservice.entity.User;
import com.aston.userservice.exception.UserNotFoundException;
import com.aston.userservice.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock UserRepository repository;
    @Mock com.aston.userservice.event.OutboxRepository outbox;
    UserService service;

    @BeforeEach
    void setUp() {
        service = new UserService(repository, outbox);
    }

    private User user() {
        User u = new User("Иван", "ivan@example.com", 25);
        ReflectionTestUtils.setField(u, "id", 1L);
        ReflectionTestUtils.setField(u, "createdAt", LocalDateTime.of(2026, 1, 1, 12, 0));
        return u;
    }

    @Test
    void createReturnsPersistedDto() {
        when(repository.saveAndFlush(any(User.class))).thenReturn(user());
        var result = service.create(new UserRequest(" Иван ", " ivan@example.com ", 25));
        assertEquals(1L, result.id());
        assertNotNull(result.createdAt());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(repository).saveAndFlush(captor.capture());
        assertEquals("Иван", captor.getValue().getName());
        assertEquals("ivan@example.com", captor.getValue().getEmail());
    }

    @Test
    void getReturnsDto() {
        when(repository.findById(1L)).thenReturn(Optional.of(user()));
        assertEquals("ivan@example.com", service.getById(1L).email());
    }

    @Test
    void missingUserThrows() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(UserNotFoundException.class, () -> service.getById(1L));
    }

    @Test
    void getAllMapsSortedRows() {
        when(repository.findAll(Sort.by("id"))).thenReturn(List.of(user()));
        assertEquals(1L, service.getAll().get(0).id());
    }

    @Test
    void updatePreservesCreationTime() {
        User u = user();
        var created = u.getCreatedAt();
        when(repository.findById(1L)).thenReturn(Optional.of(u));
        var result = service.update(1L, new UserRequest("Пётр", "petr@example.com", 30));
        assertEquals("Пётр", result.name());
        assertEquals("petr@example.com", result.email());
        assertEquals(30, result.age());
        assertEquals(created, result.createdAt());
        verify(repository).flush();
    }

    @Test
    void missingUpdateDoesNotInsert() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(
                UserNotFoundException.class,
                () -> service.update(1L, new UserRequest("Иван", "a@b.ru", 25)));
        verify(repository, never()).saveAndFlush(any());
        verify(repository, never()).flush();
    }

    @Test
    void deleteLoadsAndDeletes() {
        User u = user();
        when(repository.findById(1L)).thenReturn(Optional.of(u));
        service.delete(1L);
        verify(repository).delete(u);
        verify(repository).flush();
    }

    @Test
    void missingDeleteThrows() {
        when(repository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(UserNotFoundException.class, () -> service.delete(1L));
        verify(repository, never()).delete(any(User.class));
    }

    @Test
    void invalidIdDoesNotCallRepository() {
        assertThrows(IllegalArgumentException.class, () -> service.getById(0L));
        assertThrows(IllegalArgumentException.class, () -> service.delete(-1L));
        verifyNoInteractions(repository);
    }

    @Test
    void databaseConflictPropagates() {
        var failure = new DataIntegrityViolationException("duplicate");
        when(repository.saveAndFlush(any(User.class))).thenThrow(failure);
        assertSame(
                failure,
                assertThrows(
                        DataIntegrityViolationException.class,
                        () -> service.create(new UserRequest("Иван", "ivan@example.com", 25))));
    }
}
