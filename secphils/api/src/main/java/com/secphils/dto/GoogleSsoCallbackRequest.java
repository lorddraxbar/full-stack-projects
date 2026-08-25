package com.secphils.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Authorization-code callback posted by the SSO callback page. Only the
 * opaque code and the signed state are accepted — the identity itself is
 * established server-side by exchanging the code with Google and
 * verifying the id_token against Google's JWKS.
 */
public record GoogleSsoCallbackRequest(
        @NotBlank String code,
        @NotBlank String state
) {}
