package com.secphils.security;

import java.io.Serializable;

/**
 * Authenticated principal placed in the SecurityContext by {@link JwtAuthFilter}.
 * Loaded from the database on every request so deactivated users are rejected.
 */
public record AuthUser(Long id, String email, String role, Long companyId) implements Serializable {

    public String fullName() {
        return email;
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }

    public boolean isUserOrAdmin() {
        return "ADMIN".equals(role) || "USER".equals(role);
    }

    public boolean isClient() {
        return "CLIENT".equals(role);
    }

    /** Convenience alias matching the class-style getter convention used elsewhere. */
    public Long getCompanyId() {
        return companyId;
    }
}
