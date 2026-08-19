package com.secphils.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {
    public static TokenResponse of(String access, String refresh, long accessTtlSeconds, UserResponse user) {
        return new TokenResponse(access, refresh, "Bearer", accessTtlSeconds, user);
    }
}
