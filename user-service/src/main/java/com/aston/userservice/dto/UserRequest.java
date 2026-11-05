package com.aston.userservice.dto;

import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;

public record UserRequest(
        @Schema(description = "Непустое имя; пробелы по краям удаляются", example = "Иван")
        @NotBlank @Size(min = 1, max = 100) String name,
        @Schema(description = "Уникальный email", example = "ivan@example.com")
        @NotBlank @Email @Size(min = 1, max = 254) String email,
        @Schema(description = "Возраст в годах", example = "25")
        @NotNull @Min(0) @Max(150) Integer age) {
    public UserRequest {
        name = name == null ? null : name.strip();
        email = email == null ? null : email.strip();
    }
}
