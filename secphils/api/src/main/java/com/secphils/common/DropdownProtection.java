package com.secphils.common;

import java.util.Map;
import java.util.Set;

/**
 * The dropdown codes whose existence the BACKEND behavior keys on (V35).
 * Locked rows may be re-labeled (displayLabel) but never deleted, and their
 * code can never be renamed or shadowed by a new value.
 *
 *   project_status: COMPLETED stamps completed_at, ARCHIVED drives the
 *   archive/restore/hard-delete lifecycle; the other three complete the
 *   shipped vocabulary the donut and filters enumerate.
 *   audience: PROJECT vs COMPANY decides announcement visibility fan-out
 *   (PROJECT rows are only visible to that project's company).
 *
 * user_role is protected at the CATEGORY level instead (auth closed set).
 * announcement_category carries no locked codes: adding/removing categories
 * is genuinely safe.
 */
public final class DropdownProtection {

    private static final Map<String, Set<String>> LOCKED = Map.of(
            "project_status", Set.of("NOT_STARTED", "IN_PROGRESS", "ON_HOLD", "COMPLETED", "ARCHIVED"),
            "audience", Set.of("PROJECT", "COMPANY"));

    /**
     * Categories whose VALUE SET is structure, not vocabulary (bbb3680 review).
     * audience is a binary visibility switch: AnnouncementController.dispatch()
     * ignores it (fan-out is always whole-company) while the client-side list
     * filter only understands COMPANY and PROJECT+own-project — a third value
     * an admin invented would make announcements silently invisible to every
     * client. Renaming labels stays open; adding/deleting/re-coding does not.
     * (user_role is protectedCategory, which is stricter: no writes at all.)
     */
    private static final Set<String> FROZEN_VALUES = Set.of("audience");

    private DropdownProtection() {
    }

    public static boolean isLockedValue(String categoryName, String code) {
        return LOCKED.getOrDefault(categoryName, Set.of()).contains(code);
    }

    public static boolean isFrozenValues(String categoryName) {
        return FROZEN_VALUES.contains(categoryName);
    }
}
