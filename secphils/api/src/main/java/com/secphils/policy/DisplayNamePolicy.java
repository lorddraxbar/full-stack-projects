package com.secphils.policy;

import com.secphils.entity.SystemSettings;
import com.secphils.entity.User;
import com.secphils.repository.SystemSettingsRepository;
import com.secphils.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central display-name policy for client-facing surfaces.
 *
 * <p>Provider-side identity (roles ADMIN / USER) is never shown as a person on
 * <em>client-visible</em> surfaces: the display name collapses to the
 * configurable brand ({@code system_settings.brand_name}, default
 * {@value #DEFAULT_BRAND}) and the mail-from to a no-reply address, so a client
 * can't tell which provider sent a message, authored an announcement, uploaded
 * a document, or ran an action. Client-side identity (role CLIENT) is always
 * shown with the real name.
 *
 * <p><b>Internal (staff-only) messages are the one exception</b>: they never
 * reach a client (server-side filtered, 403 on post, 404 on attachment download),
 * so {@link com.secphils.controller.MessageController} deliberately unmasks the
 * real colleague there. That masking is a privacy guarantee for clients, not a
 * team-communication feature.
 *
 * <p>The policy is <em>viewer-independent</em>: the same value is served to
 * every viewer of a client-readable surface, including to other providers.
 * Admin management surfaces (Admin &gt; Audit Logs, Admin &gt; Users)
 * intentionally bypass this policy and read the raw {@code User} fields so
 * staff still see real names where they need them.
 *
 * <p><b>Dynamic brand.</b> The collapsed name is read from
 * {@code system_settings.brand_name}. Because the many DTO mappers call the
 * pure static {@link #nameFor(User)}, the brand is held in a
 * {@code static volatile} field (a single-API-container app, so no cross-node
 * sync is needed). It is initialised at startup and refreshed by
 * {@link #refresh()} whenever an admin saves settings (see
 * {@link com.secphils.controller.AdminController#updateSettings}). A null or
 * blank stored value falls back to {@link #DEFAULT_BRAND}.
 */
@Component
public class DisplayNamePolicy {

    /** Fallback brand used before the settings row is read / when it's blank. */
    public static final String DEFAULT_BRAND = "SECPhils";
    public static final String NO_REPLY_EMAIL = "no-reply@secphils.com";

    /** Live brand, refreshed at startup and on admin save (see class javadoc). */
    private static volatile String brand = DEFAULT_BRAND;

    private final UserRepository users;
    private final SystemSettingsRepository settings;

    public DisplayNamePolicy(UserRepository users, SystemSettingsRepository settings) {
        this.users = users;
        this.settings = settings;
        refresh();
    }

    /** Re-read the brand from the settings row (call after an admin saves settings). */
    public void refresh() {
        String stored = null;
        try {
            stored = settings.findAll().stream().findFirst()
                    .map(SystemSettings::getBrandName).orElse(null);
        } catch (Exception ignored) {
            // Fresh schema / settings not yet seeded — fall back to the default.
        }
        brand = (stored == null || stored.isBlank()) ? DEFAULT_BRAND : stored;
    }

    /** The effective collapsed-provider brand. */
    public String getBrand() {
        return brand;
    }

    /**
     * Pure variant used inside DTO mappers that already hold the loaded
     * {@link User} (avoids N+1 in list endpoints).
     * Provider → the configurable brand; client → full name (email fallback).
     */
    public static String nameFor(User u) {
        if (u == null) return null;
        if (!isClient(u)) return brand;
        String name = u.getFullName();
        return (name == null || name.isBlank()) ? u.getEmail() : name;
    }

    /**
     * Mail-from for a loaded user: client keeps their address, provider uses
     * no-reply. (Internal-message email overrides this with the real address —
     * see {@link com.secphils.controller.MessageController#dispatch}.)
     */
    public static String emailFor(User u) {
        if (u == null) return NO_REPLY_EMAIL;
        return isClient(u) ? (u.getEmail() == null || u.getEmail().isBlank() ? NO_REPLY_EMAIL : u.getEmail())
                : NO_REPLY_EMAIL;
    }

    private static boolean isClient(User u) {
        return u != null && u.getRole() != null && u.getRole().trim().equalsIgnoreCase("CLIENT");
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
