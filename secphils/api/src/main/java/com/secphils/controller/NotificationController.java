package com.secphils.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
import com.secphils.dto.NotificationResponse;
import com.secphils.entity.Notification;
import com.secphils.entity.NotificationPreference;
import com.secphils.repository.NotificationPreferenceRepository;
import com.secphils.repository.NotificationRepository;
import com.secphils.security.AuthUser;
import com.secphils.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<String, Boolean> DEFAULT_EMAIL = new LinkedHashMap<>();
    private static final Map<String, Boolean> DEFAULT_IN_APP = new LinkedHashMap<>();
    static {
        // Keys owned by the Settings UI (email-handles panel).
        for (String k : new String[]{"projectCreated", "newMessage", "projectUpdate",
                "documentUploaded", "documentRequested",
                "projectStatusChanged", "announcement", "teamInvitation"}) {
            DEFAULT_EMAIL.put(k, true);
        }
        for (String k : new String[]{"newMessage", "documentUploaded",
                "projectStatusChanged", "announcement"}) {
            DEFAULT_IN_APP.put(k, true);
        }
    }

    public NotificationController(NotificationRepository notificationRepository,
                                  NotificationPreferenceRepository preferenceRepository,
                                  AuditService auditService) {
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.auditService = auditService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<NotificationResponse>> list(
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly) {
        AuthUser me = CurrentUser.require();
        List<Notification> items = unreadOnly
                ? notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(me.id())
                : notificationRepository.findByRecipientIdOrderByCreatedAtDesc(me.id());
        return ResponseEntity.ok(items.stream().map(NotificationResponse::from).toList());
    }

    @PatchMapping("/{id}/read")
    @Transactional
    public ResponseEntity<NotificationResponse> markRead(@PathVariable Long id, HttpServletRequest http) {
        AuthUser me = CurrentUser.require();
        Notification n = notificationRepository.findById(id)
                .filter(x -> x.getRecipient() != null && me.id().equals(x.getRecipient().getId()))
                .orElseThrow(() -> ApiException.notFound("Notification"));
        n.setIsRead(true);
        notificationRepository.save(n);
        auditService.audit(me, "NOTIFICATION_READ", "Notification", n.getId(), null, http);
        return ResponseEntity.ok(NotificationResponse.from(n));
    }

    @PatchMapping("/read-all")
    @Transactional
    public ResponseEntity<Map<String, Object>> markAllRead(HttpServletRequest http) {
        AuthUser me = CurrentUser.require();
        List<Notification> unread = notificationRepository
                .findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(me.id());
        unread.forEach(n -> n.setIsRead(true));
        notificationRepository.saveAll(unread);
        auditService.audit(me, "NOTIFICATION_READ_ALL", "Notification", null,
                "Count: " + unread.size(), http);
        return ResponseEntity.ok(Map.of("updated", unread.size()));
    }

    @GetMapping("/preferences")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Map<String, Boolean>>> getPreferences() {
        AuthUser me = CurrentUser.require();
        NotificationPreference pref = preferenceRepository.findByUserId(me.id()).orElse(null);
        return ResponseEntity.ok(Map.of(
                "email", merged(DEFAULT_EMAIL, pref == null ? null : pref.getEmail()),
                "inApp", merged(DEFAULT_IN_APP, pref == null ? null : pref.getInApp())));
    }

    @PutMapping("/preferences")
    @Transactional
    public ResponseEntity<Map<String, Map<String, Boolean>>> updatePreferences(
            @RequestBody Map<String, Map<String, Boolean>> body, HttpServletRequest http) {
        AuthUser me = CurrentUser.require();
        Map<String, Boolean> email = body.get("email");
        Map<String, Boolean> inApp = body.get("inApp");
        if (email == null && inApp == null) {
            throw ApiException.badRequest("Provide an 'email' and/or 'inApp' preferences object");
        }
        NotificationPreference pref = preferenceRepository.findByUserId(me.id()).orElseGet(() -> {
            NotificationPreference p = new NotificationPreference();
            p.setUserId(me.id());
            return p;
        });
        if (email != null) pref.setEmail(writeJson(mergeMaps(DEFAULT_EMAIL, email)));
        if (inApp != null) pref.setInApp(writeJson(mergeMaps(DEFAULT_IN_APP, inApp)));
        preferenceRepository.save(pref);
        auditService.audit(me, "NOTIFICATION_PREF_UPDATE", "NotificationPreference", me.id(), null, http);
        return ResponseEntity.ok(Map.of(
                "email", readJson(pref.getEmail(), DEFAULT_EMAIL),
                "inApp", readJson(pref.getInApp(), DEFAULT_IN_APP)));
    }

    private Map<String, Boolean> merged(Map<String, Boolean> defaults, String storedJson) {
        Map<String, Boolean> result = new LinkedHashMap<>(defaults);
        if (storedJson != null) {
            try {
                result.putAll(objectMapper.readValue(storedJson, new TypeReference<Map<String, Boolean>>() {}));
            } catch (Exception e) {
                // malformed stored JSON — fall back to defaults
            }
        }
        return result;
    }

    private Map<String, Boolean> mergeMaps(Map<String, Boolean> defaults, Map<String, Boolean> incoming) {
        Map<String, Boolean> result = new LinkedHashMap<>(defaults);
        if (incoming != null) result.putAll(incoming);
        return result;
    }

    private Map<String, Boolean> readJson(String json, Map<String, Boolean> defaults) {
        return merged(defaults, json);
    }

    private String writeJson(Map<String, Boolean> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            throw ApiException.badRequest("Could not serialize notification preferences");
        }
    }
}