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
        String attachmentUrl,
        String attachmentFileName,
        Long attachmentFileSize,
        String attachmentContentType,
        /** 'CLIENT' (default) or 'INTERNAL'. Always present so the UI can flag internal bubbles. */
        String visibility,
        LocalDateTime createdAt
) {
    public static MessageResponse from(Message m) {
        return new MessageResponse(m.getId(),
                m.getProject() != null ? m.getProject().getId() : null,
                m.getSender() != null ? m.getSender().getId() : null,
                DisplayNamePolicy.nameFor(m.getSender()),
                m.getBody(),
                m.getAttachmentUrl(),
                m.getAttachmentFileName(),
                m.getAttachmentFileSize(),
                m.getAttachmentContentType(),
                m.getVisibility() == null ? "CLIENT" : m.getVisibility(),
                m.getCreatedAt());
    }
}
