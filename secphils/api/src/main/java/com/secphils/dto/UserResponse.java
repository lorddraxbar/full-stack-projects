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
        Long companyId,
        String companyName,
        LocalDateTime deactivatedAt,
        LocalDateTime createdAt,
        LocalDateTime lastLogin,
        String phone,
        String avatar,
        Boolean twoFactorEnabled,
        Boolean emailVerified
) {
    public static UserResponse from(User u) {
        return from(u, null);
    }

    public static UserResponse from(User u, String companyName) {
        return new UserResponse(u.getId(), u.getEmail(), u.getFirstName(), u.getLastName(),
                u.getFullName(), u.getRole(), u.getIsActive(), u.getCompanyId(), companyName,
                u.getDeactivatedAt(), u.getCreatedAt(), u.getLastLogin(),
                u.getPhone(), u.getAvatar(), u.getTwoFactorEnabled(), u.getEmailVerified());
    }
}
