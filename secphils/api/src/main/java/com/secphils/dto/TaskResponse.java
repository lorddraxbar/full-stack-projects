package com.secphils.dto;

import com.secphils.entity.Task;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TaskResponse(
        Long id,
        Long projectId,
        Long assigneeId,
        String assigneeName,
        String title,
        String description,
        String status,
        String priority,
        LocalDate dueDate,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static TaskResponse from(Task t) {
        return new TaskResponse(t.getId(),
                t.getProject() != null ? t.getProject().getId() : null,
                t.getAssignee() != null ? t.getAssignee().getId() : null,
                t.getAssignee() != null ? t.getAssignee().getFullName() : null,
                t.getTitle(), t.getDescription(), t.getStatus(), t.getPriority(),
                t.getDueDate(), t.getCreatedAt(), t.getUpdatedAt());
    }
}
