package com.aston.userservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;

@Entity
@Table(name = "users")
public class User {
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "email", unique = true, nullable = false, length = 254)
    private String email;

    @Column(name = "age", nullable = false)
    private Integer age;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected User() {
        // Конструктор для Hibernate.
    }

    public User(String name, String email, Integer age) {
        setName(name);
        setEmail(email);
        setAge(age);
    }

    @PrePersist
    private void onCreate() {
        createdAt = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public Integer getAge() {
        return age;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setName(String name) {
        if (name == null || name.isBlank() || name.strip().length() > 100) {
            throw new IllegalArgumentException("Имя должно содержать от 1 до 100 символов.");
        }
        this.name = name.strip();
    }

    public void setEmail(String email) {
        if (email == null
                || email.strip().length() > 254
                || !EMAIL_PATTERN.matcher(email.strip()).matches()) {
            throw new IllegalArgumentException(
                    "Введите корректный email, например user@example.com.");
        }
        this.email = email.strip();
    }

    public void setAge(Integer age) {
        if (age == null || age < 0 || age > 150) {
            throw new IllegalArgumentException("Возраст должен быть от 0 до 150.");
        }
        this.age = age;
    }

    @Override
    public String toString() {
        return "User{"
                + "id="
                + id
                + ", name='"
                + name
                + '\''
                + ", email='"
                + email
                + '\''
                + ", age="
                + age
                + ", createdAt="
                + createdAt
                + '}';
    }
}
