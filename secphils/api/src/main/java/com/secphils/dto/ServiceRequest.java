package com.secphils.dto;

import jakarta.validation.constraints.NotBlank;

public record ServiceRequest(
        @NotBlank String name,
        String description,
        String category,
        Boolean isActive,
        String icon,
        Integer sortOrder
) {}
