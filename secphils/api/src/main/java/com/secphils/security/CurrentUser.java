package com.secphils.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Helper to extract the authenticated principal. */
public final class CurrentUser {

    private CurrentUser() {}

    public static AuthUser get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser user) {
            return user;
        }
        return null;
    }

    public static AuthUser require() {
        AuthUser user = get();
        if (user == null) {
            throw new com.secphils.common.ApiException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return user;
    }
}
