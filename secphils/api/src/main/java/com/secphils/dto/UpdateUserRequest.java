package com.secphils.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Email String email,
        @Size(min = 8, max = 100) String password,
        String firstName,
        String lastName,
        String role,
        Boolean isActive
) {}
