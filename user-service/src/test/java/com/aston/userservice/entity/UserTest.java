package com.aston.userservice.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class UserTest {
    @Test
    void stripsInput() {
        User user = new User("  Иван  ", " ivan@example.com ", 25);
        assertEquals("Иван", user.getName());
        assertEquals("ivan@example.com", user.getEmail());
    }

    @Test
    void rejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> new User(" ", "a@b.ru", 20));
        assertThrows(IllegalArgumentException.class, () -> new User("Иван", "bad", 20));
        assertThrows(IllegalArgumentException.class, () -> new User("Иван", "a b@c.ru", 20));
        assertThrows(IllegalArgumentException.class, () -> new User("Иван", "a@b.ru", -1));
        assertThrows(IllegalArgumentException.class, () -> new User("Иван", "a@b.ru", 151));
        assertThrows(IllegalArgumentException.class, () -> new User("Иван", "a@b.ru", null));
    }

    @Test
    void invalidSetterLeavesPreviousValue() {
        User user = new User("Иван", "ivan@example.com", 25);
        assertThrows(IllegalArgumentException.class, () -> user.setEmail("bad"));
        assertEquals("ivan@example.com", user.getEmail());
    }
}
