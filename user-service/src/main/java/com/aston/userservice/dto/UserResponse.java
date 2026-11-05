package com.aston.userservice.dto;

import java.time.LocalDateTime;
import org.springframework.hateoas.server.core.Relation;
import io.swagger.v3.oas.annotations.media.Schema;

@Relation(itemRelation = "user", collectionRelation = "users")
@Schema(description = "Пользователь; id и createdAt назначаются сервером")
public record UserResponse(
        @Schema(description = "ID, назначенный сервером", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
        Long id,
        @Schema(description = "Имя пользователя", example = "Иван") String name,
        @Schema(description = "Email пользователя", example = "ivan@example.com") String email,
        @Schema(description = "Возраст в годах", example = "25") Integer age,
        @Schema(description = "Время создания в локальном времени сервера, без часового пояса; при обновлении не меняется",
                example = "2026-10-27T12:00:00", accessMode = Schema.AccessMode.READ_ONLY)
        LocalDateTime createdAt) {}
