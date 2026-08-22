package com.secphils.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Second step of a 2FA-required login: pending token + TOTP code. */
public record TwoFactorLoginRequest(
        @NotBlank(message = "Pending token is required") String pendingToken,
        @NotBlank(message = "Verification code is required") @Size(min = 6, max = 6) String code
) {}