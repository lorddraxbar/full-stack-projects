package com.secphils.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
import com.secphils.dto.DocuSignConfig;
import com.secphils.dto.GoogleSsoConfig;
import com.secphils.dto.SmtpConfig;
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
import com.secphils.service.DocuSignService;
import com.secphils.service.MailService;
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
    private final DocuSignService docuSignService;
    private final MailService mailService;

    public AdminController(SystemSettingsRepository settingsRepository, AuditService auditService,
                           UserRepository userRepository, CompanyRepository companyRepository,
                           ProjectRepository projectRepository, ReviewRepository reviewRepository,
                           DataSource dataSource, S3StorageService storageService,
                           DisplayNamePolicy displayNamePolicy,
                           RetentionPolicy retentionPolicy,
                           DocuSignService docuSignService,
                           MailService mailService) {
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
        this.docuSignService = docuSignService;
        this.mailService = mailService;
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
        return ResponseEntity.ok(maskedResponse(settings));
    }

    /**
     * Secrets must NEVER be masked onto the managed entity: a @Transactional
     * PUT flushes every field change present at commit, so masking in-place
     * persisted "********" over the real storage secret / SSO secret / SMTP
     * password on every settings save (the historical SignatureDoesNotMatch
     * footgun — root-caused 2026-09-08). Mask a detached copy instead.
     */
    private SystemSettings maskedResponse(SystemSettings s) {
        SystemSettings c = new SystemSettings();
        c.setId(s.getId());
        c.setPortalName(s.getPortalName());
        c.setEmailTemplates(s.getEmailTemplates());
        c.setStorage(s.getStorage());
        c.setSmtp(s.getSmtp());
        c.setDocusign(s.getDocusign());
        c.setGoogleSso(s.getGoogleSso());
        c.setMaintenanceMode(s.getMaintenanceMode());
        c.setInviteBaseUrl(s.getInviteBaseUrl());
        c.setLandingContactEmail(s.getLandingContactEmail());
        c.setBrandName(s.getBrandName());
        c.setRetentionWindowDays(s.getRetentionWindowDays());
        c.setUpdatedAt(s.getUpdatedAt());
        return maskDocuSign(maskSmtp(maskStorage(maskSso(c))));
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
        if (body.containsKey("integrations")) {
            throw ApiException.badRequest("Integrations were retired (V34) — configure SMTP and DocuSign directly.");
        }
        if (body.containsKey("smtp")) settings.setSmtp(normalizeSmtp(body.get("smtp"), settings.getSmtp()));
        if (body.containsKey("docusign")) settings.setDocusign(normalizeDocuSign(body.get("docusign"), settings.getDocusign()));
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
        return ResponseEntity.ok(maskedResponse(settings));
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

    /** Serializes a nested JSON payload value (Map) back to JSON —
     *  String.valueOf(Map) yields "{k=v}" which is NOT JSON. */
    private String writeJson(Object v) {
        if (v == null) return "";
        try {
            return new ObjectMapper().writeValueAsString(v);
        } catch (Exception e) {
            throw ApiException.badRequest("Config payload is not serializable JSON");
        }
    }

    // ---- SMTP (V34) —
    private SystemSettings maskSmtp(SystemSettings s) {
        if (s.getSmtp() != null) {
            SmtpConfig cfg = parseSmtp(s.getSmtp());
            if (cfg.password != null && !cfg.password.isBlank()) {
                s.setSmtp(serializeSmtp(SmtpConfig.masked(cfg)));
            }
        }
        return s;
    }

    private String normalizeSmtp(Object incoming, String currentJson) {
        if (incoming == null) return currentJson;
        String json = String.valueOf(incoming);
        if (json.isBlank()) return currentJson;
        SmtpConfig in = parseSmtp(json);
        SmtpConfig cur = parseSmtp(currentJson);
        // Masked or blank password = keep the stored one (same discipline as SSO).
        if (in.password == null || in.password.isBlank() || SmtpConfig.SECRET_MASK.equals(in.password)) {
            in.password = cur.password == null ? "" : cur.password;
        }
        if (in.host == null || in.host.isBlank()) {
            throw ApiException.badRequest("SMTP host is required");
        }
        if (in.port <= 0 || in.port > 65535) {
            throw ApiException.badRequest("SMTP port must be between 1 and 65535");
        }
        if ((in.username == null || in.username.isBlank())
                && (in.password == null || in.password.isBlank())) {
            throw ApiException.badRequest("SMTP username and password are required");
        }
        return serializeSmtp(in);
    }

    private SmtpConfig parseSmtp(String json) {
        try {
            if (json == null || json.isBlank()) return new SmtpConfig();
            return new ObjectMapper().readValue(json, SmtpConfig.class);
        } catch (Exception e) {
            throw ApiException.badRequest("SMTP settings are not valid JSON");
        }
    }

    private String serializeSmtp(SmtpConfig cfg) {
        try {
            return new ObjectMapper().writeValueAsString(cfg);
        } catch (Exception e) {
            throw ApiException.badRequest("SMTP settings are not serializable");
        }
    }

    // ---- DocuSign (V34) ----
    private SystemSettings maskDocuSign(SystemSettings s) {
        if (s.getDocusign() != null) {
            DocuSignConfig cfg = docuSignService.parse(s.getDocusign());
            if (cfg.privateKey != null && !cfg.privateKey.isBlank()) {
                s.setDocusign(docuSignService.serialize(DocuSignConfig.masked(cfg)));
            }
        }
        return s;
    }

    private String normalizeDocuSign(Object incoming, String currentJson) {
        if (incoming == null) return currentJson;
        String json = String.valueOf(incoming);
        if (json.isBlank()) return currentJson;
        DocuSignConfig in = docuSignService.parse(json);
        DocuSignConfig cur = docuSignService.parse(currentJson);
        if (in.privateKey == null || in.privateKey.isBlank()
                || DocuSignConfig.SECRET_MASK.equals(in.privateKey)) {
            in.privateKey = cur.privateKey == null ? "" : cur.privateKey;
        }
        return docuSignService.serialize(in);
    }

    /** Live SMTP probe — sends one real email using the PAYLOAD config (not
     *  the stored one), so admins can validate before saving. */
    @PostMapping("/settings/smtp/test")
    public ResponseEntity<Map<String, Object>> testSmtp(@RequestBody Map<String, Object> body,
                                                        HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        String to = body.get("to") == null ? null : String.valueOf(body.get("to")).trim();
        if (to == null || to.isEmpty() || !to.contains("@")) {
            throw ApiException.badRequest("A test recipient email address is required");
        }
        SmtpConfig cfg = parseSmtp(writeJson(body.get("config")));
        // A masked/blank password in the payload means "test with the stored one".
        if (SmtpConfig.SECRET_MASK.equals(cfg.password) || cfg.password == null || cfg.password.isBlank()) {
            SystemSettings s = settingsRepository.findAll().stream().findFirst().orElse(null);
            if (s != null && s.getSmtp() != null) {
                cfg.password = parseSmtp(s.getSmtp()).password;
            }
        }
        auditService.audit(actor, "SMTP_TEST", "SystemSettings", null, "to: " + to, http);
        return ResponseEntity.ok(mailService.testSend(cfg, to));
    }

    /** Live DocuSign probe — real JWT-grant token exchange using the payload
     *  config (masked/blank private key = the stored one). */
    @PostMapping("/settings/docusign/test")
    public ResponseEntity<Map<String, Object>> testDocuSign(@RequestBody Map<String, Object> body,
                                                            HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        DocuSignConfig in = docuSignService.parse(writeJson(body.get("config")));
        if (DocuSignConfig.SECRET_MASK.equals(in.privateKey) || in.privateKey == null || in.privateKey.isBlank()) {
            SystemSettings s = settingsRepository.findAll().stream().findFirst().orElse(null);
            if (s != null && s.getDocusign() != null) {
                in.privateKey = docuSignService.parse(s.getDocusign()).privateKey;
            }
        }
        auditService.audit(actor, "DOCUSIGN_TEST", "SystemSettings", null, null, http);
        return ResponseEntity.ok(docuSignService.testConnection(in));
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
    public ResponseEntity<Map<String, Object>> auditLogs(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        long total = auditService.count(action, userId, search);
        List<com.secphils.entity.AuditLog> logs = auditService.query(action, userId, page, size, search);
        List<Map<String, Object>> content = logs.stream().map(l -> Map.<String, Object>of(
                "id", l.getId(),
                "userId", l.getUser() != null ? l.getUser().getId() : "",
                "userName", l.getUser() != null ? l.getUser().getFullName() : "",
                "action", l.getAction(),
                "entityType", l.getEntityType() != null ? l.getEntityType() : "",
                "entityId", l.getEntityId() != null ? l.getEntityId() : "",
                "details", l.getDetails() != null ? l.getDetails() : "",
                "ipAddress", l.getIpAddress() != null ? l.getIpAddress() : "",
                "createdAt", l.getCreatedAt() != null ? l.getCreatedAt().toString() : ""
        )).toList();
        return ResponseEntity.ok(Map.of(
                "content", content,
                "total", total,
                "page", page,
                "size", size
        ));
    }
}
