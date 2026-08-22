package com.secphils.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

@Service
public class JwtService {

    public enum TokenType { ACCESS, REFRESH, PENDING_2FA }

    private final SecretKey key;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final Duration pendingTfaTtl;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-ttl}") Duration accessTtl,
            @Value("${app.jwt.refresh-token-ttl}") Duration refreshTtl,
            @Value("${app.jwt.pending-2fa-token-ttl:5m}") Duration pendingTfaTtl) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
        this.pendingTfaTtl = pendingTfaTtl;
    }

    public String generate(Long userId, String email, String role, TokenType type) {
        Duration ttl = switch (type) {
            case ACCESS -> accessTtl;
            case REFRESH -> refreshTtl;
            case PENDING_2FA -> pendingTfaTtl;
        };
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim("role", role)
                .claim("type", type.name())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttl.toMillis()))
                .signWith(key)
                .compact();
    }

    /** Returns the claims if the token is valid and of the expected type; throws otherwise. */
    public Claims parse(String token, TokenType expectedType) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!expectedType.name().equals(claims.get("type", String.class))) {
            throw new JwtException("Wrong token type");
        }
        return claims;
    }

    public Long userId(Claims claims) {
        return Long.valueOf(claims.getSubject());
    }

    public String email(Claims claims) {
        return claims.get("email", String.class);
    }

    public String role(Claims claims) {
        return claims.get("role", String.class);
    }

    public static class JwtException extends RuntimeException {
        public JwtException(String message) {
            super(message);
        }
    }
}
