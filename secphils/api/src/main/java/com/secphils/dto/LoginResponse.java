package com.secphils.dto;

import java.time.LocalDateTime;

/**
 * Login response. When 2FA is enabled on the account the server returns
 * {@code requires2fa: true} plus a short-lived {@code pendingToken} rather than
 * access tokens; the client then exchanges {@code pendingToken + code} at
 * {@code POST /auth/2fa/verify}.
 */
public record LoginResponse(
        Boolean requires2fa,
        String pendingToken,
        String accessToken,
        String refreshToken,
        String tokenType,
        Long expiresIn,
        UserResponse user
) {}