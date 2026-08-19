package com.secphils.dto;

import com.secphils.entity.Notification;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        String title,
        String body,
        String type,
        String entityType,
        Long entityId,
        Boolean isRead,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(n.getId(), n.getTitle(), n.getBody(), n.getType(),
                n.getEntityType(), n.getEntityId(), n.getIsRead(), n.getCreatedAt());
    }
}
