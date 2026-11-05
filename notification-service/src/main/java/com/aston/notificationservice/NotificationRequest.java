package com.aston.notificationservice;

import jakarta.validation.constraints.*;

public record NotificationRequest(
        @NotNull Operation operation,
        @NotBlank @Email @Size(max = 254) String email) {
    public enum Operation { CREATED, DELETED }
    public NotificationRequest { if (email != null) email = email.strip(); }
}
