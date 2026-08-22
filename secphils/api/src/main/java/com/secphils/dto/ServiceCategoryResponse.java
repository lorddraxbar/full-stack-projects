package com.secphils.dto;

import com.secphils.entity.ServiceCategory;

import java.time.LocalDateTime;

public record ServiceCategoryResponse(
        Long id,
        String name,
        String icon,
        Integer sortOrder,
        int serviceCount,
        LocalDateTime createdAt
) {
    public static ServiceCategoryResponse from(ServiceCategory c, int serviceCount) {
        return new ServiceCategoryResponse(c.getId(), c.getName(), c.getIcon(),
                c.getSortOrder(), serviceCount, c.getCreatedAt());
    }
}
