package com.aston.userservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.aston.userservice.controller.UserController;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI userApi() {
        return new OpenAPI().info(new Info().title("User Service API").version("1.0")
                .description("Управление пользователями. Ответы HAL содержат _links; список — _embedded.users. "
                        + "self/users: GET, update: PUT, delete: DELETE. Ошибки: application/problem+json."));
    }

    @Bean
    OperationCustomizer errorResponses() {
        return (operation, handler) -> {
            if (handler.getBeanType() != UserController.class) return operation;
            String method = handler.getMethod().getName();
            operation.getResponses().addApiResponse("503", error("База данных временно недоступна"));
            if (!method.equals("getAll")) operation.getResponses().addApiResponse("400", error("Некорректный ID, JSON или поля запроса"));
            if (!method.equals("getAll") && !method.equals("create")) operation.getResponses().addApiResponse("404", error("Пользователь не найден"));
            if (method.equals("create") || method.equals("update") || method.equals("delete"))
                operation.getResponses().addApiResponse("409", error("Нарушение ограничения целостности; email должен быть уникальным"));
            return operation;
        };
    }

    private ApiResponse error(String description) {
        Schema<?> schema = new Schema<>().type("object")
                .addProperty("type", new Schema<>().type("string").format("uri"))
                .addProperty("title", new Schema<>().type("string"))
                .addProperty("status", new Schema<>().type("integer"))
                .addProperty("detail", new Schema<>().type("string"))
                .addProperty("instance", new Schema<>().type("string"))
                .addProperty("errors", new Schema<>().type("object").additionalProperties(new Schema<>().type("string")));
        return new ApiResponse().description(description).content(new Content().addMediaType(
                "application/problem+json", new io.swagger.v3.oas.models.media.MediaType().schema(schema)));
    }
}
