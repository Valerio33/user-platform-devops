package com.aston.userservice.controller;

import com.aston.userservice.dto.*;
import com.aston.userservice.service.UserService;
import jakarta.validation.Valid;
import org.springframework.hateoas.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.headers.Header;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.*;

@RestController
@RequestMapping(value = "/api/users", produces = {"application/hal+json", "application/json"})
@Tag(name = "Пользователи", description = "CRUD пользователей. Ответы содержат HAL-ссылки; сущности JPA не возвращаются.")
public class UserController {
    private final UserService service;
    private final UserModelAssembler assembler;

    public UserController(UserService service, UserModelAssembler assembler) {
        this.service = service;
        this.assembler = assembler;
    }

    @GetMapping
    @Operation(summary = "Получить всех пользователей", description = "Список находится в _embedded.users. Ссылка self ведёт на коллекцию.")
    @ApiResponse(responseCode = "200", description = "Коллекция пользователей, включая пустую")
    public UserCollection getAll() {
        return new UserCollection(service.getAll().stream().map(assembler::toModel).toList())
                .add(linkTo(methodOn(UserController.class).getAll()).withSelfRel());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Найти пользователя по ID")
    @ApiResponse(responseCode = "200", description = "Пользователь со ссылками self, users, update и delete")
    public EntityModel<UserResponse> getById(@Parameter(description = "ID пользователя", example = "1") @PathVariable Long id) {
        return assembler.toModel(service.getById(id));
    }

    @PostMapping(consumes = "application/json")
    @Operation(summary = "Создать пользователя")
    @ApiResponse(responseCode = "201", description = "Пользователь создан", headers = @Header(name = "Location", description = "URL созданного пользователя"))
    public ResponseEntity<EntityModel<UserResponse>> create(@Valid @RequestBody UserRequest request) {
        var model = assembler.toModel(service.create(request));
        return ResponseEntity.created(model.getRequiredLink("self").toUri()).body(model);
    }

    @PutMapping(value = "/{id}", consumes = "application/json")
    @Operation(summary = "Обновить пользователя", description = "Все три поля обязательны. id и createdAt не меняются. Ссылка update используется с методом PUT.")
    @ApiResponse(responseCode = "200", description = "Обновлённый пользователь")
    public EntityModel<UserResponse> update(@Parameter(description = "ID пользователя", example = "1") @PathVariable Long id, @Valid @RequestBody UserRequest request) {
        return assembler.toModel(service.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Удалить пользователя", description = "Ссылка delete используется с методом DELETE. Повторное удаление возвращает 404.")
    @ApiResponse(responseCode = "204", description = "Пользователь удалён; тело отсутствует", content = @Content,
            headers = @Header(name = "Link", description = "Ссылка на коллекцию с отношением users"))
    public ResponseEntity<Void> delete(@Parameter(description = "ID пользователя", example = "1") @PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().header("Link",
                linkTo(methodOn(UserController.class).getAll()).withRel("users").toString()).build();
    }
}
