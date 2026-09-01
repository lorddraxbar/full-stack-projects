package com.secphils.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MessageRequest(
        @NotNull Long projectId,
        @NotBlank String body,
        /** 'CLIENT' (default) or 'INTERNAL' — internal is rejected for CLIENT-role senders. */
        String visibility
) {}
