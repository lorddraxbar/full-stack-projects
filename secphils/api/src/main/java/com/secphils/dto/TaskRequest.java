package com.secphils.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record TaskRequest(
        @NotNull Long projectId,
        Long assigneeId,
        @NotBlank String title,
        String description,
        String status,
        String priority,
        LocalDate dueDate,
        List<SubtaskItem> subtasks
) {}
