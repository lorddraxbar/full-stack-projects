package com.secphils.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
) {
    public static TokenResponse of(String access, String refresh, long accessTtlSeconds) {
        return new TokenResponse(access, refresh, "Bearer", accessTtlSeconds);
    }
}
