package com.secphils.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secphils.entity.Task;
import com.secphils.policy.DisplayNamePolicy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
        List<SubtaskItem> subtasks,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<SubtaskItem>> SUBTASK_LIST = new TypeReference<>() {};

    /** Decode the opaque JSONB column; malformed/legacy values degrade to an empty list. */
    static List<SubtaskItem> subtasksOf(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) return List.of();
        try {
            List<SubtaskItem> items = MAPPER.readValue(json, SUBTASK_LIST);
            return items == null ? List.of() : items;
        } catch (Exception e) {
            return List.of();
        }
    }

    public static TaskResponse from(Task t) {
        return new TaskResponse(t.getId(),
                t.getProject() != null ? t.getProject().getId() : null,
                t.getAssignee() != null ? t.getAssignee().getId() : null,
                DisplayNamePolicy.nameFor(t.getAssignee()),
                t.getTitle(), t.getDescription(), t.getStatus(), t.getPriority(),
                t.getDueDate(), subtasksOf(t.getSubtasks()), t.getCreatedAt(), t.getUpdatedAt());
    }
}
