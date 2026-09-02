package com.secphils.dto;

/**
 * Login response. When 2FA is enabled on the account the server returns
 * {@code requires2fa: true} plus a short-lived {@code pendingToken} rather than
 * access tokens; the client then exchanges {@code pendingToken + code} at
 * {@code POST /auth/2fa/verify}.
 *
 * <p>{@code portalName} (the admin-configurable app title) and {@code brand}
 * (the admin-configurable brand: collapsed provider sender name + drawer
 * wordmark) are echoed on every login path so the client can render them
 * without a separate settings fetch.
 */
public record LoginResponse(
        Boolean requires2fa,
        String pendingToken,
        String accessToken,
        String refreshToken,
        String tokenType,
        Long expiresIn,
        UserResponse user,
        String portalName,
        String brand
) {}
