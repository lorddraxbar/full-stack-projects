package com.secphils.dto;

import com.secphils.entity.Message;

import java.time.LocalDateTime;

public record MessageResponse(
        Long id,
        Long projectId,
        Long senderId,
        String senderName,
        String body,
        LocalDateTime createdAt
) {
    public static MessageResponse from(Message m) {
        return new MessageResponse(m.getId(),
                m.getProject() != null ? m.getProject().getId() : null,
                m.getSender() != null ? m.getSender().getId() : null,
                m.getSender() != null ? m.getSender().getFullName() : null,
                m.getBody(), m.getCreatedAt());
    }
}
