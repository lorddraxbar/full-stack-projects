package com.secphils.dto;

import jakarta.validation.constraints.Email;

public record UpdateUserRequest(
        @Email String email,
        String firstName,
        String lastName,
        String role,
        String password,
        Long companyId,
        Boolean isActive
) {}
