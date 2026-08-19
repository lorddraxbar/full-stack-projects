package com.secphils.security;

import java.io.Serializable;

/**
 * Authenticated principal placed in the SecurityContext by {@link JwtAuthFilter}.
 * Loaded from the database on every request so deactivated users are rejected.
 */
public record AuthUser(Long id, String email, String role) implements Serializable {

    public String fullName() {
        return email;
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public boolean isProviderOrAdmin() {
        return "ADMIN".equals(role) || "PROVIDER".equals(role);
    }
}
