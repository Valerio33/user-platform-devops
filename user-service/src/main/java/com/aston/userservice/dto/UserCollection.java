package com.aston.userservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.RepresentationModel;
import java.util.List;

@Schema(description = "HAL-коллекция. users всегда присутствует, даже если список пуст.")
public class UserCollection extends RepresentationModel<UserCollection> {
    private final Embedded embedded;

    public UserCollection(List<EntityModel<UserResponse>> users) {
        this.embedded = new Embedded(List.copyOf(users));
    }

    @JsonProperty("_embedded")
    public Embedded getEmbedded() {
        return embedded;
    }

    public record Embedded(List<EntityModel<UserResponse>> users) {}
}
