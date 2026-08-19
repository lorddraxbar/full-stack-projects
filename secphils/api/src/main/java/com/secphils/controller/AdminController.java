package com.secphils.controller;

import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
import com.secphils.entity.SystemSettings;
import com.secphils.repository.SystemSettingsRepository;
import com.secphils.security.AuthUser;
import com.secphils.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final SystemSettingsRepository settingsRepository;
    private final AuditService auditService;

    public AdminController(SystemSettingsRepository settingsRepository, AuditService auditService) {
        this.settingsRepository = settingsRepository;
        this.auditService = auditService;
    }

    @GetMapping("/settings")
    @Transactional(readOnly = true)
    public ResponseEntity<SystemSettings> getSettings() {
        SystemSettings settings = settingsRepository.findAll().stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("System settings"));
        return ResponseEntity.ok(settings);
    }

    @PutMapping("/settings")
    @Transactional
    public ResponseEntity<SystemSettings> updateSettings(@RequestBody Map<String, Object> body,
                                                         HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        SystemSettings settings = settingsRepository.findAll().stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("System settings"));
        if (body.containsKey("portalName")) settings.setPortalName((String) body.get("portalName"));
        if (body.containsKey("emailTemplates")) settings.setEmailTemplates((String) body.get("emailTemplates"));
        if (body.containsKey("integrations")) settings.setIntegrations((String) body.get("integrations"));
        if (body.containsKey("securityPolicies")) settings.setSecurityPolicies((String) body.get("securityPolicies"));
        if (body.containsKey("maintenanceMode")) {
            settings.setMaintenanceMode(Boolean.valueOf(String.valueOf(body.get("maintenanceMode"))));
        }
        settings.setUpdatedAt(LocalDateTime.now());
        settings = settingsRepository.save(settings);
        auditService.audit(actor, "SETTINGS_UPDATE", "SystemSettings", settings.getId(), null, http);
        return ResponseEntity.ok(settings);
    }

    @GetMapping("/audit-logs")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> auditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "100") int limit) {
        List<com.secphils.entity.AuditLog> logs = auditService.query(action, userId, limit);
        return ResponseEntity.ok(logs.stream().map(l -> Map.<String, Object>of(
                "id", l.getId(),
                "userId", l.getUser() != null ? l.getUser().getId() : "",
                "action", l.getAction(),
                "entityType", l.getEntityType() != null ? l.getEntityType() : "",
                "entityId", l.getEntityId() != null ? l.getEntityId() : "",
                "details", l.getDetails() != null ? l.getDetails() : "",
                "ipAddress", l.getIpAddress() != null ? l.getIpAddress() : "",
                "createdAt", l.getCreatedAt() != null ? l.getCreatedAt().toString() : ""
        )).toList());
    }
}
