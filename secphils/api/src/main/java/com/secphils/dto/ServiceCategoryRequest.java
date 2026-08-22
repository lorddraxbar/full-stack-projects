package com.secphils.dto;

import jakarta.validation.constraints.NotBlank;

public record ServiceCategoryRequest(
        @NotBlank String name,
        String icon,
        Integer sortOrder
) {}
