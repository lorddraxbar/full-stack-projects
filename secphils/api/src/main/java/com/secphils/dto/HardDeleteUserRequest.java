package com.secphils.dto;

import jakarta.validation.constraints.NotBlank;

public record HardDeleteUserRequest(
        @NotBlank(message = "Password is required for immediate hard delete") String password
) {}
