package com.secphils.dto;

import jakarta.validation.constraints.NotBlank;

public record AnnouncementRequest(
        Long companyId,
        Long projectId,
        @NotBlank String title,
        @NotBlank String body,
        String category,
        String audience,
        Boolean isPublished
) {}
