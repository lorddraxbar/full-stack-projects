package com.secphils.dto;

import com.secphils.entity.Document;

import java.time.LocalDateTime;

public record DocumentResponse(
        Long id,
        Long projectId,
        Long uploaderId,
        String uploaderName,
        String title,
        String description,
        String category,
        String fileUrl,
        Long fileSize,
        Integer version,
        Boolean isLatest,
        LocalDateTime uploadedAt
) {
    public static DocumentResponse from(Document d) {
        return new DocumentResponse(d.getId(),
                d.getProject() != null ? d.getProject().getId() : null,
                d.getUploader() != null ? d.getUploader().getId() : null,
                d.getUploader() != null ? d.getUploader().getFullName() : null,
                d.getTitle(), d.getDescription(), d.getCategory(), d.getFileUrl(),
                d.getFileSize(), d.getVersion(), d.getIsLatest(), d.getUploadedAt());
    }
}
