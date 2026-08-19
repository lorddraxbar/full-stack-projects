package com.secphils.dto;

import jakarta.validation.constraints.NotNull;

public record TeamMemberRequest(@NotNull Long userId) {}
