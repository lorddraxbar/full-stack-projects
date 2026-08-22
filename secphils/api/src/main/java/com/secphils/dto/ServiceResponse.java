package com.secphils.dto;

import com.secphils.entity.Service;

import java.time.LocalDateTime;

public record ServiceResponse(
        Long id,
        String name,
        String description,
        String category,
        Long categoryId,
        Boolean isActive,
        String icon,
        Integer sortOrder,
        LocalDateTime deactivatedAt,
        LocalDateTime createdAt
) {
    public static ServiceResponse from(Service s) {
        String categoryName = s.getCategory() != null ? s.getCategory().getName() : null;
        Long categoryId = s.getCategory() != null ? s.getCategory().getId() : null;
        return new ServiceResponse(s.getId(), s.getName(), s.getDescription(),
                categoryName, categoryId, s.getIsActive(), s.getIcon(), s.getSortOrder(),
                s.getDeactivatedAt(), s.getCreatedAt());
    }
}
