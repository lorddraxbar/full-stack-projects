package com.secphils.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * SSO callback payload. In production the token would be verified against the
 * provider's token endpoint; this stub accepts the verified identity directly.
 */
public record SsoCallbackRequest(
        @Email @NotBlank String email,
        @NotBlank String firstName,
        @NotBlank String lastName
) {}
