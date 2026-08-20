package com.secphils.dto;

import java.util.List;

public record RoleResponse(
        Long id,
        String name,
        String description,
        String userType,
        Boolean isSystem,
        List<Long> permissionIds,
        Long assignedUserCount
) {}
