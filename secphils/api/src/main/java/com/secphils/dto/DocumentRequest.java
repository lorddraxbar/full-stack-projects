package com.secphils.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record DocumentRequest(
        @NotNull Long projectId,
        @NotBlank String title,
        String description,
        String category,
        String fileUrl,
        Long fileSize
) {}
