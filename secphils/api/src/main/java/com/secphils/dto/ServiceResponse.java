package com.secphils.dto;

import com.secphils.entity.Service;

import java.time.LocalDateTime;

public record ServiceResponse(
        Long id,
        String name,
        String description,
        String category,
        Boolean isActive,
        String icon,
        Integer sortOrder,
        LocalDateTime createdAt
) {
    public static ServiceResponse from(Service s) {
        return new ServiceResponse(s.getId(), s.getName(), s.getDescription(),
                s.getCategory(), s.getIsActive(), s.getIcon(), s.getSortOrder(), s.getCreatedAt());
    }
}
