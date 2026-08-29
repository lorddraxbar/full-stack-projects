package com.secphils.dto;

import com.secphils.entity.Document;
import com.secphils.policy.DisplayNamePolicy;

import java.time.LocalDateTime;

public record DocumentResponse(
        Long id,
        Long projectId,
        Long uploaderId,
        String uploaderName,
        String title,
        String description,
        String fileType,
        String fileName,
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
                DisplayNamePolicy.nameFor(d.getUploader()),
                d.getTitle(), d.getDescription(), d.fileType(), d.fileName(), d.getFileUrl(),
                d.getFileSize(), d.getVersion(), d.getIsLatest(), d.getUploadedAt());
    }
}
