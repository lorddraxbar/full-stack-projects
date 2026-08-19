package com.secphils.dto;

import com.secphils.entity.Announcement;

import java.time.LocalDateTime;

public record AnnouncementResponse(
        Long id,
        Long companyId,
        Long projectId,
        String title,
        String body,
        String category,
        String audience,
        Boolean isPublished,
        Long createdById,
        String createdByName,
        LocalDateTime createdAt
) {
    public static AnnouncementResponse from(Announcement a) {
        return new AnnouncementResponse(a.getId(),
                a.getCompany() != null ? a.getCompany().getId() : null,
                a.getProject() != null ? a.getProject().getId() : null,
                a.getTitle(), a.getBody(), a.getCategory(), a.getAudience(),
                a.getIsPublished(),
                a.getCreatedBy() != null ? a.getCreatedBy().getId() : null,
                a.getCreatedBy() != null ? a.getCreatedBy().getFullName() : null,
                a.getCreatedAt());
    }
}
