package com.secphils.dto;

import jakarta.validation.constraints.NotBlank;

public record DocumentCommentRequest(@NotBlank String comment) {}
