package com.secphils.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TwoFactorRequest(
        @NotBlank @Size(min = 6, max = 6) String code
) {}
