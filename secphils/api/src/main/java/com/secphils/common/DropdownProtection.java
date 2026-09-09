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
     * Categories the SPA forms read BY NAME (V35 wiring). Renaming or deleting
     * them silently un-wires the form (the composable falls back to built-ins),
     * so their identity is locked; adding/editing/deleting their VALUES stays
     * open — that IS the feature.
     */
    private static final Set<String> WIRED = Set.of(
            "project_status", "announcement_category", "audience");

    private DropdownProtection() {
    }

    public static boolean isLockedValue(String categoryName, String code) {
        return LOCKED.getOrDefault(categoryName, Set.of()).contains(code);
    }

    public static boolean isWiredCategory(String categoryName) {
        return WIRED.contains(categoryName);
    }
}
