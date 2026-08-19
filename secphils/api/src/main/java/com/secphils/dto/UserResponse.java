package com.secphils.dto;

import com.secphils.entity.User;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String email,
        String firstName,
        String lastName,
        String fullName,
        String role,
        Boolean isActive,
        LocalDateTime createdAt,
        LocalDateTime lastLogin
) {
    public static UserResponse from(User u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(),
                u.getFullName(), u.getRole(), u.getIsActive(), u.getCreatedAt(), u.getLastLogin());
    }
}
