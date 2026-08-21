package com.secphils.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Public contact form payload (landing page "Get Started" / "Say hello" form).
 * Delivered as email to the provider's Company Profile "Email Addresses".
 */
public record LandingContactRequest(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 100) String phone,
        @NotBlank @Size(max = 2000) String message
) {}
