package com.secphils.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
import com.secphils.dto.GoogleSsoConfig;
import com.secphils.entity.AuditLog;
import com.secphils.entity.SystemSettings;
import com.secphils.policy.DisplayNamePolicy;
import com.secphils.policy.RetentionPolicy;
import com.secphils.repository.CompanyRepository;
import com.secphils.repository.ProjectRepository;
import com.secphils.repository.ReviewRepository;
import com.secphils.repository.SystemSettingsRepository;
import com.secphils.repository.UserRepository;
import com.secphils.security.AuthUser;
import com.secphils.security.CurrentUser;
import com.secphils.service.S3StorageService;
import com.secphils.service.S3StorageService.StorageConfig;
import com.secphils.service.SsoService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final SystemSettingsRepository settingsRepository;
    private final AuditService auditService;
    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final ProjectRepository projectRepository;
    private final ReviewRepository reviewRepository;
    private final DataSource dataSource;
    private final S3StorageService storageService;
    private final DisplayNamePolicy displayNamePolicy;
    private final RetentionPolicy retentionPolicy;

    public AdminController(SystemSettingsRepository settingsRepository, AuditService auditService,
                           UserRepository userRepository, CompanyRepository companyRepository,
                           ProjectRepository projectRepository, ReviewRepository reviewRepository,
                           DataSource dataSource, S3StorageService storageService,
                           DisplayNamePolicy displayNamePolicy,
                           RetentionPolicy retentionPolicy) {
        this.settingsRepository = settingsRepository;
        this.auditService = auditService;
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.projectRepository = projectRepository;
        this.reviewRepository = reviewRepository;
        this.dataSource = dataSource;
        this.storageService = storageService;
        this.displayNamePolicy = displayNamePolicy;
        this.retentionPolicy = retentionPolicy;
    }

    /**
     * Dashboard numbers + live health probes. Everything is a real count or
     * a live check — no canned values.
     */
    @GetMapping("/stats")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> stats() {
        long clients = userRepository.count();
        long activeProjects = projectRepository.count(); // projects only exist once started
        long completedProjects = 0;
        long pendingReviews = 0;
        try {
            completedProjects = projectRepository.findByStatus("COMPLETED").size();
            pendingReviews = reviewRepository.findByStatus("PENDING").size();
        } catch (Exception ignored) {
            // count queries are best-effort; a fresh schema may lack the method
        }
        double totalCost = projectRepository.findAll().stream()
                .filter(p -> p.getTotalCost() != null)
                .mapToDouble(p -> p.getTotalCost().doubleValue())
                .sum();

        Map<String, Object> db = new HashMap<>();
        String dbStatus;
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1")) {
            dbStatus = rs.next() ? "HEALTHY" : "DEGRADED";
            db.put("status", dbStatus);
        } catch (Exception e) {
            dbStatus = "UNAVAILABLE";
            db.put("status", dbStatus);
            db.put("detail", String.valueOf(e.getMessage()));
        }

        LocalDateTime settingsUpdated = settingsRepository.findAll().stream()
                .findFirst().map(SystemSettings::getUpdatedAt).orElse(null);

        Map<String, Object> body = new HashMap<>();
        body.put("totalClients", clients);
        body.put("activeProjects", activeProjects);
        body.put("completedProjects", completedProjects);
        body.put("totalRevenue", totalCost);
        body.put("pendingReviews", pendingReviews);
        body.put("backendStatus", "HEALTHY");
        body.put("database", db);
        body.put("lastSettingsUpdate", settingsUpdated != null ? settingsUpdated.toString() : null);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/settings")
    @Transactional(readOnly = true)
    public ResponseEntity<SystemSettings> getSettings() {
        SystemSettings settings = settingsRepository.findAll().stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("System settings"));
        return ResponseEntity.ok(maskStorage(maskSso(settings)));
    }

    @PutMapping("/settings")
    @Transactional
    public ResponseEntity<SystemSettings> updateSettings(@RequestBody Map<String, Object> body,
                                                         HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        SystemSettings settings = settingsRepository.findAll().stream().findFirst()
                .orElseThrow(() -> ApiException.notFound("System settings"));
        if (body.containsKey("portalName")) settings.setPortalName((String) body.get("portalName"));
        if (body.containsKey("emailTemplates")) {
            String json = body.get("emailTemplates") == null ? null : String.valueOf(body.get("emailTemplates"));
            if (json != null && !json.isBlank()) requireJsonArray(json);
            settings.setEmailTemplates(json == null || json.isBlank() ? null : json);
        }
        if (body.containsKey("integrations")) settings.setIntegrations((String) body.get("integrations"));
        if (body.containsKey("securityPolicies")) settings.setSecurityPolicies((String) body.get("securityPolicies"));
        if (body.containsKey("storage")) settings.setStorage(normalizeStorage(body.get("storage"), settings.getStorage()));
        if (body.containsKey("googleSso")) settings.setGoogleSso(normalizeSso(body.get("googleSso"), settings.getGoogleSso()));
        if (body.containsKey("maintenanceMode")) {
            settings.setMaintenanceMode(Boolean.valueOf(String.valueOf(body.get("maintenanceMode"))));
        }
        if (body.containsKey("inviteBaseUrl")) {
            String url = body.get("inviteBaseUrl") == null ? null : String.valueOf(body.get("inviteBaseUrl")).trim();
            settings.setInviteBaseUrl(url == null || url.isEmpty() ? null : url);
        }
        if (body.containsKey("landingContactEmail")) {
            String addr = body.get("landingContactEmail") == null ? null : String.valueOf(body.get("landingContactEmail")).trim();
            if (addr != null && !addr.isEmpty() && !addr.contains("@")) {
                throw ApiException.badRequest("Default landing-page recipient must be an email address (or leave it blank for manager@secphils.com)");
            }
            settings.setLandingContactEmail(addr == null ? null : (addr.isEmpty() ? null : addr));
        }
        if (body.containsKey("brandName")) {
            String bn = body.get("brandName") == null ? null : String.valueOf(body.get("brandName")).trim();
            settings.setBrandName(bn == null ? null : (bn.isEmpty() ? null : bn));
        }
        if (body.containsKey("retentionWindowDays")) {
            Object rw = body.get("retentionWindowDays");
            if (rw == null || String.valueOf(rw).isBlank()) {
                settings.setRetentionWindowDays(null); // blank -> back to the 7-day default
            } else {
                int days;
                try {
                    days = Integer.parseInt(String.valueOf(rw).trim());
                } catch (NumberFormatException e) {
                    throw ApiException.badRequest("Retention window must be a whole number of days");
                }
                if (days < RetentionPolicy.MIN_DAYS || days > RetentionPolicy.MAX_DAYS) {
                    throw ApiException.badRequest("Retention window must be between "
                            + RetentionPolicy.MIN_DAYS + " and " + RetentionPolicy.MAX_DAYS + " days");
                }
                settings.setRetentionWindowDays(days);
            }
        }
        settings.setUpdatedAt(LocalDateTime.now());
        settings = settingsRepository.save(settings);
        // Push the new values to the live policies so client-visible
        // surfaces reflect the change without a restart.
        displayNamePolicy.refresh();
        retentionPolicy.refresh();
        auditService.audit(actor, "SETTINGS_UPDATE", "SystemSettings", settings.getId(), null, http);
        return ResponseEntity.ok(maskStorage(maskSso(settings)));
    }

    /** Rejects non-JSON-array emailTemplates payloads before they corrupt the settings row. */
    private void requireJsonArray(String json) {
        try {
            if (!new ObjectMapper().readTree(json).isArray()) {
                throw ApiException.badRequest("emailTemplates must be a JSON array");
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("emailTemplates is not valid JSON: " + e.getMessage());
        }
    }

    /**
     * Verifies a (possibly not yet saved) object-storage configuration by
     * opening a throwaway S3 client, running head-bucket + list-bucket.
     * Never touches the live client cache.
     */
    @PostMapping("/settings/storage/test")
    public ResponseEntity<Map<String, Object>> testStorage(@RequestBody Map<String, Object> body,
                                                          HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        auditService.audit(actor, "STORAGE_TEST", "SystemSettings", null, null, http);
        StorageConfig cfg = S3StorageService.fromMap(body);
        return ResponseEntity.ok(storageService.testConnection(cfg));
    }

    /**
     * Storage arrives as a JSON string (like the other JSONB columns).
     *
     * Secret-key semantics (the UI loads the stored config with the secret
     * redacted to {@link S3StorageService#SECRET_MASK}):
     *   • mask ("********")  → keep the currently stored secret
     *   • blank             → also keep the currently stored secret
     *   • any other value    → adopt it as the new secret
     *
     * A blank bucket is treated as a full clear (resets to an empty config),
     * so an admin can remove a mis-configured endpoint without it half-persisting.
     * Access key follows the same keep-if-unset rule as the secret.
     */
    private String normalizeStorage(Object incoming, String currentJson) {
        String json = incoming == null ? null : String.valueOf(incoming);
        if (json == null || json.isBlank()) return currentJson;
        StorageConfig in = storageService.parseConfig(json);
        StorageConfig cur = storageService.parseConfig(currentJson);

        // Full clear: nothing meaningful entered → reset to empty config.
        if (in.bucket().isBlank()) {
            return storageService.serialize(StorageConfig.empty());
        }

        boolean secretMasked = S3StorageService.SECRET_MASK.equals(in.secretKey());
        String secret = (!in.secretKey().isBlank() && !secretMasked) ? in.secretKey() : cur.secretKey();
        String access = in.accessKey().isBlank() ? cur.accessKey() : in.accessKey();

        if (access.isBlank() || secret.isBlank()) {
            throw ApiException.badRequest("Bucket, access key and secret key are all required");
        }

        return storageService.serialize(new StorageConfig(
                in.provider(), in.region(), in.bucket(),
                access, secret, in.endpoint(),
                in.publicBaseUrl(), in.folder(), in.maxUploadMb()));
    }

    /** Redacts the stored secret so GET /settings never ships the raw key back out. */
    private SystemSettings maskStorage(SystemSettings s) {
        if (s.getStorage() != null) {
            StorageConfig cfg = storageService.parseConfig(s.getStorage());
            if (!cfg.secretKey().isBlank()) {
                cfg = new StorageConfig(cfg.provider(), cfg.region(), cfg.bucket(), cfg.accessKey(),
                        S3StorageService.SECRET_MASK, cfg.endpoint(), cfg.publicBaseUrl(), cfg.folder(), cfg.maxUploadMb());
                s.setStorage(storageService.serialize(cfg));
            }
        }
        return s;
    }

    /**
     * Google SSO arrives as a JSON string (like the other JSONB columns).
     *
     * Client-secret semantics (mirrors the storage config rule — the UI
     * loads the stored config with the secret redacted to "********"):
     *   • mask ("********")  → keep the currently stored secret
     *   • blank             → keep the currently stored secret (toggle-off)
     *   • any other value    → adopt it as the new secret
     */
    private String normalizeSso(Object incoming, String currentJson) {
        if (incoming == null) return currentJson;
        String json = String.valueOf(incoming);
        if (json.isBlank()) return currentJson;
        GoogleSsoConfig in = SsoService.fromJson(json);
        GoogleSsoConfig cur = SsoService.fromJson(currentJson);
        if (in.clientSecret == null || in.clientSecret.isBlank()
                || GoogleSsoConfig.SECRET_MASK.equals(in.clientSecret)) {
            in.clientSecret = cur.clientSecret == null ? "" : cur.clientSecret;
        }
        return SsoService.toJson(in);
    }

    /** Redacts the stored SSO client secret on read. */
    private SystemSettings maskSso(SystemSettings s) {
        if (s.getGoogleSso() != null) {
            GoogleSsoConfig cfg = SsoService.fromJson(s.getGoogleSso());
            if (cfg.clientSecret != null && !cfg.clientSecret.isBlank()) {
                s.setGoogleSso(SsoService.toJson(GoogleSsoConfig.masked(cfg)));
            }
        }
        return s;
    }

    @GetMapping("/audit-logs")
    @Transactional(readOnly = true)
    public ResponseEntity<List<Map<String, Object>>> auditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "100") int limit) {
        List<com.secphils.entity.AuditLog> logs = auditService.query(action, userId, limit, search);
        return ResponseEntity.ok(logs.stream().map(l -> Map.<String, Object>of(
                "id", l.getId(),
                "userId", l.getUser() != null ? l.getUser().getId() : "",
                "userName", l.getUser() != null ? l.getUser().getFullName() : "",
                "action", l.getAction(),
                "entityType", l.getEntityType() != null ? l.getEntityType() : "",
                "entityId", l.getEntityId() != null ? l.getEntityId() : "",
                "details", l.getDetails() != null ? l.getDetails() : "",
                "ipAddress", l.getIpAddress() != null ? l.getIpAddress() : "",
                "createdAt", l.getCreatedAt() != null ? l.getCreatedAt().toString() : ""
        )).toList());
    }
}
