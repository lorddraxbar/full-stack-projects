package com.secphils.policy;

import com.secphils.entity.User;
import com.secphils.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central display-name policy for client-facing surfaces.
 *
 * <p>Provider-side identity (roles ADMIN / USER) is never shown as a person:
 * the display name collapses to the brand {@value #BRAND} and the mail-from
 * address to a no-reply address, so neither a client nor a staff member can
 * tell which provider sent a message, authored an announcement, uploaded a
 * document, or ran an action.
 *
 * <p>Client-side identity (role CLIENT) is always shown with the real name.
 *
 * <p>The policy is <em>viewer-independent</em>: the same value is served to
 * every viewer of a client-readable surface, including to other providers.
 * Admin management surfaces (Admin &gt; Audit Logs, Admin &gt; Users)
 * intentionally bypass this policy and read the raw {@code User} fields so
 * staff still see real names where they need them.
 *
 * <p>Notification fan-outs (in-app notification titles and emails) are
 * rendered identically for every recipient of a client-readable fan-out —
 * recipients see the same brand-or-name string the portal surface would
 * show. Mail-from for a provider sender is the no-reply address; the
 * audit trail (Admin &gt; Audit Logs) keeps the real identity.
 */
@Component
public class DisplayNamePolicy {

    public static final String BRAND = "SECPhils";
    public static final String NO_REPLY_EMAIL = "no-reply@secphils.com";

    /**
     * Pure, repository-free variant for use inside DTO mappers that already
     * hold the loaded {@code User} (avoids N+1 in list endpoints).
     * Provider → {@link #BRAND}; client → full name (email fallback).
     */
    public static String nameFor(User u) {
        if (u == null) return null;
        if (!isClient(u)) return BRAND;
        String name = u.getFullName();
        return (name == null || name.isBlank()) ? u.getEmail() : name;
    }

    /** Mail-from for a loaded user: client keeps their address, provider uses no-reply. */
    public static String emailFor(User u) {
        if (u == null) return NO_REPLY_EMAIL;
        return isClient(u) ? (u.getEmail() == null || u.getEmail().isBlank() ? NO_REPLY_EMAIL : u.getEmail())
                : NO_REPLY_EMAIL;
    }

    private static boolean isClient(User u) {
        return u != null && u.getRole() != null && u.getRole().trim().equalsIgnoreCase("CLIENT");
    }

    private final UserRepository users;

    public DisplayNamePolicy(UserRepository users) {
        this.users = users;
    }

    /** Resolve a user id to its display name (extra fetch; use {@link #nameFor} when the entity is in hand). */
    public String displayName(Long id) {
        if (id == null) return "";
        return users.findById(id).map(u -> nameFor(u) == null ? "" : nameFor(u)).orElse("");
    }

    /** Resolve a user id to its mail-from address. */
    public String emailAddress(Long id) {
        if (id == null) return NO_REPLY_EMAIL;
        return users.findById(id).map(DisplayNamePolicy::emailFor).orElse(NO_REPLY_EMAIL);
    }

    /** Batched id → display name (for DTO lists that only have ids). */
    public Map<Long, String> displayNames(List<Long> ids) {
        Map<Long, String> out = new HashMap<>();
        if (ids == null) return out;
        for (Long id : ids) {
            if (id == null) continue;
            out.put(id, displayName(id));
        }
        return out;
    }
}
