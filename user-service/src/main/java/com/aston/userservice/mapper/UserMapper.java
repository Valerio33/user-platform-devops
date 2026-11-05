package com.aston.userservice.mapper;

import com.aston.userservice.dto.UserResponse;
import com.aston.userservice.entity.User;

public final class UserMapper {
    private UserMapper() {}

    public static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(), user.getName(), user.getEmail(), user.getAge(), user.getCreatedAt());
    }
}
