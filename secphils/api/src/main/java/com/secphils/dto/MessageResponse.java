package com.secphils.dto;

import com.secphils.entity.Message;
import com.secphils.policy.DisplayNamePolicy;

import java.time.LocalDateTime;

public record MessageResponse(
        Long id,
        Long projectId,
        Long senderId,
        String senderName,
        String body,
        String attachmentFileName,
        Long attachmentFileSize,
        String attachmentContentType,
        LocalDateTime createdAt
) {
    public static MessageResponse from(Message m) {
        return new MessageResponse(m.getId(),
                m.getProject() != null ? m.getProject().getId() : null,
                m.getSender() != null ? m.getSender().getId() : null,
                DisplayNamePolicy.nameFor(m.getSender()),
                m.getBody(),
                m.getAttachmentFileName(),
                m.getAttachmentFileSize(),
                m.getAttachmentContentType(),
                m.getCreatedAt());
    }
}
