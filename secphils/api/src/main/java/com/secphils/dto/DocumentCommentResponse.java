package com.secphils.dto;

import com.secphils.entity.DocumentComment;
import com.secphils.policy.DisplayNamePolicy;

import java.time.LocalDateTime;

public record DocumentCommentResponse(
        Long id,
        Long documentId,
        Long userId,
        String userName,
        String comment,
        LocalDateTime createdAt
) {
    public static DocumentCommentResponse from(DocumentComment c) {
        return new DocumentCommentResponse(c.getId(),
                c.getDocument() != null ? c.getDocument().getId() : null,
                c.getUser() != null ? c.getUser().getId() : null,
                DisplayNamePolicy.nameFor(c.getUser()),
                c.getComment(), c.getCreatedAt());
    }
}
