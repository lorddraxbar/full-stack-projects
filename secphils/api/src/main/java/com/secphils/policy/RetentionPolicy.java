package com.secphils.policy;

import com.secphils.entity.SystemSettings;
import com.secphils.repository.SystemSettingsRepository;
import org.springframework.stereotype.Component;

/**
 * Central retention policy — how long a deactivated entity stays recoverable
 * before it can be (or gets) permanently removed.
 *
 * <p>One admin-configurable value ({@code system_settings.retention_window_days},
 * default {@value #DEFAULT_DAYS}) drives every retention window in the app:
 * archived projects (hard delete goes passwordless once {@code delete_at}
 * passes), deactivated users and services (same), and trashed documents
 * (auto-purged once the window closes).
 *
 * <p>Like {@link DisplayNamePolicy}, the value lives in a
 * {@code static volatile} field (single-API-container app, no cross-node
 * sync needed): initialised at startup and re-read by
 * {@link #refresh()} whenever an admin saves settings, so a change takes
 * effect immediately without a restart. A null or out-of-range stored value
 * falls back to {@link #DEFAULT_DAYS}.
 */
@Component
public class RetentionPolicy {

    /** Fallback window used before the settings row is read / when it's blank or invalid. */
    public static final int DEFAULT_DAYS = 7;
    /** Hard bounds for the admin setting (sanity, not a policy). */
    public static final int MIN_DAYS = 1;
    public static final int MAX_DAYS = 365;

    /** Live window in days, refreshed at startup and on admin save. */
    private static volatile int days = DEFAULT_DAYS;

    private final SystemSettingsRepository settings;

    public RetentionPolicy(SystemSettingsRepository settings) {
        this.settings = settings;
        refresh();
    }

    /** Re-read the window from the settings row (call after an admin saves settings). */
    public void refresh() {
        Integer stored = null;
        try {
            stored = settings.findAll().stream().findFirst()
                    .map(SystemSettings::getRetentionWindowDays).orElse(null);
        } catch (Exception ignored) {
            // Fresh schema / settings not yet seeded — fall back to the default.
        }
        if (stored != null && stored >= MIN_DAYS && stored <= MAX_DAYS) {
            days = stored;
        } else {
            days = DEFAULT_DAYS;
        }
    }

    /** The effective retention window in days (always within [MIN_DAYS, MAX_DAYS]). */
    public int getDays() {
        return days;
    }
}
