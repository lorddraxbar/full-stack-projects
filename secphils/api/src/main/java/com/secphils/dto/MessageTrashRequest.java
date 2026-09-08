package com.secphils.dto;

import jakarta.validation.constraints.NotBlank;

/** Body for trashing a message: the reason is required and lands in the audit trail. */
public record MessageTrashRequest(@NotBlank String reason) {}
