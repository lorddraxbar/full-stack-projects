package com.secphils.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Finish enabling 2FA: the generated secret + the user's current TOTP code. */
public record VerifyTwoFactorEnableRequest(
        @NotBlank(message = "Secret is required") String secret,
        @NotBlank(message = "Verification code is required") @Size(min = 6, max = 6) String code
) {}