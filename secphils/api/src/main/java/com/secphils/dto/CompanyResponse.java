package com.secphils.dto;

import com.secphils.entity.Company;
import com.secphils.entity.User;

import java.time.LocalDateTime;

public record CompanyResponse(
        Long id,
        String name,
        String location,
        String owner,
        String description,
        Long authorizedRepId,
        String authorizedRepName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CompanyResponse from(Company c) {
        User rep = c.getAuthorizedRep();
        return new CompanyResponse(c.getId(), c.getName(), c.getLocation(), c.getOwner(),
                c.getDescription(),
                rep != null ? rep.getId() : null,
                rep != null ? rep.getFullName() : null,
                c.getCreatedAt(), c.getUpdatedAt());
    }
}
