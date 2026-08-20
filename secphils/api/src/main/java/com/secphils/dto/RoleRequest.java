package com.secphils.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RoleRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 20) String userType,
        String description,
        List<Long> permissionIds
) {}
