package com.secphils.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Single source of truth for the notification preference key set (V40).
 * {@code NotificationController} owns these keys for the logged-in matrix;
 * this mirrors them so the public unsubscribe page and the SES webhook can
 * gate/label categories without a controller→service dependency cycle.
 * If the controller's key set changes, change it HERE too — the
 * verify-unsubscribe suite asserts the two stay identical.
 *
 * {@link #MANDATORY} keys, if ever added, render locked-on with a reason on
 * the unsubscribe surfaces. It is deliberately EMPTY today: the always-sent
 * emails (invites, a removed member's own access notice, account security)
 * sit OUTSIDE this key set and bypass the preference gate entirely, so they
 * are neither toggled here nor rendered as locked switches — that would be
 * dead-switch theater (they are instead named in the page's "always sent"
 * copy). Address-wide hard stops come from email_suppressions (V40).
 */
@Service
public class NotificationPrefs {

    private static final List<String> ORDER = List.of(
            "projectCreated", "newMessage", "documentUploaded",
            "projectStatusChanged", "announcement", "authorizedRepChanged",
            "reviewSubmitted", "teamMemberRemoved", "documentDeletionRequested");

    public static final Set<String> KEYS = new LinkedHashSet<>(ORDER);

    /** Keys that must render locked-on (currently none — see class doc). */
    public static final Set<String> MANDATORY = Set.of();

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("projectCreated", "Project created"),
            Map.entry("newMessage", "New message"),
            Map.entry("documentUploaded", "Document uploaded"),
            Map.entry("projectStatusChanged", "Project status changed"),
            Map.entry("announcement", "Announcement"),
            Map.entry("authorizedRepChanged", "Authorized rep changed"),
            Map.entry("reviewSubmitted", "Review submitted"),
            Map.entry("teamMemberRemoved", "Account access changes"),
            Map.entry("documentDeletionRequested", "Document deletion requested"));

    private final ObjectMapper objectMapper = new ObjectMapper();

    public Map<String, Boolean> defaults() {
        Map<String, Boolean> m = new LinkedHashMap<>();
        for (String k : ORDER) m.put(k, true);
        return m;
    }

    public boolean isKnown(String key) {
        return key != null && KEYS.contains(key);
    }

    public String label(String key) {
        return LABELS.getOrDefault(key, key);
    }

    /** stored JSON over defaults; unknown/legacy keys pruned (never rendered). */
    public Map<String, Boolean> merged(String storedJson) {
        Map<String, Boolean> result = defaults();
        if (storedJson != null && !storedJson.isBlank()) {
            try {
                Map<String, Boolean> stored = objectMapper.readValue(storedJson,
                        new TypeReference<Map<String, Boolean>>() {});
                stored.forEach((k, v) -> { if (KEYS.contains(k) && v != null) result.put(k, v); });
            } catch (Exception e) {
                // malformed stored JSON — defaults
            }
        }
        return result;
    }

    public String writeJson(Map<String, Boolean> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not serialize notification preferences");
        }
    }

    /** Same semantics as the fan-out prefAllows: missing key/JSON = allowed. */
    public boolean prefAllows(String channelJson, String key) {
        if (channelJson == null || channelJson.isBlank()) return true;
        try {
            Map<?, ?> m = objectMapper.readValue(channelJson, Map.class);
            Object v = m.get(key);
            return v == null || Boolean.TRUE.equals(v);
        } catch (Exception e) {
            return true;
        }
    }
}
