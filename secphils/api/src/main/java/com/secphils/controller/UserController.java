package com.secphils.controller;

import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
import com.secphils.dto.HardDeleteUserRequest;
import com.secphils.dto.SetPasswordRequest;
import com.secphils.dto.UpdateUserRequest;
import com.secphils.dto.UserResponse;
import com.secphils.entity.Company;
import com.secphils.entity.SystemSettings;
import com.secphils.entity.User;
import com.secphils.repository.CompanyRepository;
import com.secphils.repository.SystemSettingsRepository;
import com.secphils.repository.UserRepository;
import com.secphils.security.CurrentUser;
import com.secphils.security.AuthUser;
import com.secphils.service.MailService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final SystemSettingsRepository settingsRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final MailService mailService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
    private final String inviteBaseUrl;
    private final Duration inviteTtl;

    public UserController(UserRepository userRepository, CompanyRepository companyRepository,
                          SystemSettingsRepository settingsRepository, PasswordEncoder passwordEncoder,
                          AuditService auditService, MailService mailService,
                          @Value("${app.invite.base-url}") String inviteBaseUrl,
                          @Value("${app.invite.token-ttl:24h}") Duration inviteTtl) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.settingsRepository = settingsRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.mailService = mailService;
        this.inviteBaseUrl = inviteBaseUrl;
        this.inviteTtl = inviteTtl;
    }

    @GetMapping("/me")
    @Transactional(readOnly = true)
    public ResponseEntity<UserResponse> me() {
        AuthUser me = CurrentUser.require();
        User user = userRepository.findById(me.id())
                .orElseThrow(() -> ApiException.notFound("User"));
        return ResponseEntity.ok(UserResponse.from(user, companyName(user)));
    }

    /** Communication settings for the USER-role admin app (stored as jsonb on the user row). */
    @GetMapping("/me/communication")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> myCommunication() {
        AuthUser me = CurrentUser.require();
        User user = userRepository.findById(me.id())
                .orElseThrow(() -> ApiException.notFound("User"));
        return ResponseEntity.ok(readCommunication(user));
    }

    @PutMapping("/me/communication")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateMyCommunication(
            @RequestBody Map<String, Object> body, HttpServletRequest http) {
        AuthUser me = CurrentUser.require();
        User user = userRepository.findById(me.id())
                .orElseThrow(() -> ApiException.notFound("User"));
        Map<String, Object> current = readCommunication(user);
        current.putAll(body); // partial updates merge over stored values
        try {
            user.setCommunicationPrefs(objectMapper.writeValueAsString(current));
        } catch (Exception e) {
            throw ApiException.badRequest("Could not serialize communication settings");
        }
        userRepository.save(user);
        auditService.audit(me, "USER_COMMUNICATION_UPDATE", "User", user.getId(), null, http);
        return ResponseEntity.ok(readCommunication(user));
    }

    private Map<String, Object> readCommunication(User user) {
        Map<String, Object> defaults = new java.util.LinkedHashMap<>();
        defaults.put("emailSignature", true);
        defaults.put("autoReply", true);
        defaults.put("autoReplyText", "Thank you for your message. Our team will respond within one business day.");
        defaults.put("callNotifications", true);
        defaults.put("messageNotifications", true);
        defaults.put("quietHours", false);
        if (user.getCommunicationPrefs() == null || user.getCommunicationPrefs().isBlank()) {
            return defaults;
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(user.getCommunicationPrefs(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            defaults.putAll(parsed);
        } catch (Exception e) {
            // malformed stored JSON — fall back to defaults
        }
        return defaults;
    }

    @PutMapping("/me")
    @Transactional
    public ResponseEntity<UserResponse> updateMe(@Valid @RequestBody UpdateUserRequest req,
                                                 HttpServletRequest http) {
        AuthUser me = CurrentUser.require();
        User user = userRepository.findById(me.id())
                .orElseThrow(() -> ApiException.notFound("User"));
        if (req.email() != null && !req.email().isBlank()) {
            final Long currentId = user.getId();
            userRepository.findByEmail(req.email())
                    .filter(u -> !u.getId().equals(currentId))
                    .ifPresent(u -> {
                        throw ApiException.conflict("Email already in use");
                    });
            user.setEmail(req.email());
        }
        if (req.firstName() != null && !req.firstName().isBlank()) user.setFirstName(req.firstName());
        if (req.lastName() != null && !req.lastName().isBlank()) user.setLastName(req.lastName());
        if (req.phone() != null) user.setPhone(req.phone());
        if (req.avatar() != null) user.setAvatar(req.avatar());
        // Self may link/unlink their own company (used by the Company Settings tab).
        if (req.companyId() != null) {
            if (companyRepository.findById(req.companyId()).isEmpty()) {
                throw ApiException.badRequest("Selected company does not exist");
            }
            user.setCompanyId(req.companyId());
        }
        // self cannot change own role or active status
        user = userRepository.save(user);
        auditService.audit(me, "USER_UPDATE_SELF", "User", user.getId(), null, http);
        return ResponseEntity.ok(UserResponse.from(user, companyName(user)));
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<UserResponse>> list() {
        // Batch-load company names so the user table doesn't N+1
        Map<Long, String> companyNames = companyRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(Company::getId, Company::getName, (a, b) -> a));
        List<UserResponse> users = userRepository.findAll().stream()
                .map(u -> UserResponse.from(u, u.getCompanyId() != null ? companyNames.get(u.getCompanyId()) : null))
                .toList();
        return ResponseEntity.ok(users);
    }

    @PostMapping
    @Transactional
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UpdateUserRequest req,
                                               HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        if (req.email() == null || req.email().isBlank()
                || req.firstName() == null || req.firstName().isBlank()
                || req.lastName() == null || req.lastName().isBlank()) {
            throw ApiException.badRequest("email, firstName and lastName are required");
        }
        if (userRepository.findByEmail(req.email()).isPresent()) {
            throw ApiException.conflict("A user with this email already exists");
        }
        if (req.companyId() != null && companyRepository.findById(req.companyId()).isEmpty()) {
            throw ApiException.badRequest("Selected company does not exist");
        }
        User user = new User();
        user.setEmail(req.email());
        user.setFirstName(req.firstName());
        user.setLastName(req.lastName());
        user.setRole(req.role() != null ? req.role() : "CLIENT");
        // For provider staff (USER/ADMIN), default the company to the acting admin's own company
        // (the provider company) unless an explicit company was chosen.
        Long companyId = req.companyId();
        if (companyId == null && !"CLIENT".equals(user.getRole())) {
            companyId = userRepository.findById(actor.id())
                    .map(User::getCompanyId)
                    .orElse(null);
        }
        user.setCompanyId(companyId);
        // New users start inactive — they activate by setting their own password via the invite link
        user.setIsActive(false);
        user = userRepository.save(user);
        issueInvite(user, actor, http);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user, companyName(user)));
    }

    @PostMapping("/{id}/resend-invite")
    @Transactional
    public ResponseEntity<Map<String, String>> resendInvite(@PathVariable Long id, HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        User user = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        issueInvite(user, actor, http);
        return ResponseEntity.ok(Map.of("message", "Invite link re-sent to " + user.getEmail()));
    }

    /**
     * Public: lets an invited (inactive) user set their own password with the one-time
     * token from the invite email. Succeeds even if the token was already used
     * (idempotent — clicking the link twice is fine).
     */
    @PostMapping("/set-password")
    @Transactional
    public ResponseEntity<Map<String, String>> setPassword(@Valid @RequestBody SetPasswordRequest req,
                                                           HttpServletRequest http) {
        User user = userRepository.findByPasswordResetToken(req.token())
                .orElseThrow(() -> ApiException.badRequest("Invalid or expired link. Please ask your admin to resend the invite."));
        if (user.getPasswordHash() != null) {
            // Already activated — treat as success so a double-click doesn't error
            return ResponseEntity.ok(Map.of("message", "Your password is already set. You can sign in now."));
        }
        if (user.getPasswordResetExpiresAt() == null || user.getPasswordResetExpiresAt().isBefore(LocalDateTime.now())) {
            throw ApiException.badRequest("Invalid or expired link. Please ask your admin to resend the invite.");
        }
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setIsActive(true);
        user.setDeactivatedAt(null);
        user.setPasswordResetToken(null);
        user.setPasswordResetExpiresAt(null);
        user.setPasswordResetRequestedAt(null);
        userRepository.save(user);
        auditService.audit(null, "USER_SET_PASSWORD", "User", user.getId(), "Email: " + user.getEmail(), http);
        return ResponseEntity.ok(Map.of("message", "Password set. You can now sign in."));
    }

    private void issueInvite(User user, AuthUser actor, HttpServletRequest http) {
        String token = newToken();
        user.setPasswordResetToken(token);
        user.setPasswordResetExpiresAt(LocalDateTime.now().plus(inviteTtl));
        user.setPasswordResetRequestedAt(LocalDateTime.now());
        userRepository.save(user);
        String link = resolveInviteBaseUrl(http) + "/auth/set-password?token=" + token;
        mailService.sendHtml(user.getEmail(), mailService.inviteSubject(),
                mailService.inviteEmail(user.getFirstName(), user.getFullName(), link), link);
        auditService.audit(actor, "USER_INVITE_SENT", "User", user.getId(), "Email: " + user.getEmail(), http);
    }

    /**
     * Invite link base URL, in priority order:
     * 1. The admin-set value in System Settings (explicit, wins).
     * 2. The host the admin is currently using (Origin/Referer) — dynamic, so a
     *    fresh deployment works without any configuration.
     * 3. The INVITE_BASE_URL environment variable as a last resort (e.g. non-browser callers).
     */
    private String resolveInviteBaseUrl(HttpServletRequest http) {
        String fromSettings = settingsRepository.findAll().stream().findFirst()
                .map(SystemSettings::getInviteBaseUrl)
                .filter(s -> s != null && !s.isBlank())
                .orElse(null);
        if (fromSettings != null) return fromSettings.replaceAll("/+$", "");
        String origin = http.getHeader("Origin");
        if (origin == null || origin.isBlank()) {
            String referer = http.getHeader("Referer");
            if (referer != null && !referer.isBlank()) {
                try {
                    java.net.URI u = java.net.URI.create(referer);
                    if (u.getScheme() != null && u.getAuthority() != null) {
                        origin = u.getScheme() + "://" + u.getAuthority();
                    }
                } catch (Exception ignored) {
                    // malformed referer — fall through
                }
            }
        }
        if (origin != null && !origin.isBlank()) return origin.replaceAll("/+$", "");
        return inviteBaseUrl.replaceAll("/+$", "");
    }

    private String companyName(User user) {
        if (user.getCompanyId() == null) return null;
        return companyRepository.findById(user.getCompanyId()).map(Company::getName).orElse(null);
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<UserResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody UpdateUserRequest req,
                                               HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        if (actor.id().equals(id)) {
            throw ApiException.forbidden("You cannot edit your own account here — use Settings");
        }
        User user = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        if (req.email() != null && !req.email().isBlank()) {
            final Long currentId = user.getId();
            userRepository.findByEmail(req.email())
                    .filter(u -> !u.getId().equals(currentId))
                    .ifPresent(u -> {
                        throw ApiException.conflict("Email already in use");
                    });
            user.setEmail(req.email());
        }
        if (req.password() != null && !req.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(req.password()));
        }
        if (req.firstName() != null && !req.firstName().isBlank()) user.setFirstName(req.firstName());
        if (req.lastName() != null && !req.lastName().isBlank()) user.setLastName(req.lastName());
        if (req.role() != null && !req.role().isBlank()) user.setRole(req.role());
        if (req.isActive() != null) user.setIsActive(req.isActive());
        // Phone is applied on non-null so a blank value clears it (the null-safe
        // name/email fields above only apply when non-blank).
        if (req.phone() != null) user.setPhone(req.phone());
        if (req.companyId() != null) {
            if (companyRepository.findById(req.companyId()).isEmpty()) {
                throw ApiException.badRequest("Selected company does not exist");
            }
            user.setCompanyId(req.companyId());
        } else if (req.companyId() == null && req.role() != null && !"CLIENT".equals(req.role())) {
            // Switching a user to provider staff without an explicit company:
            // default to the acting admin's own (provider) company.
            Long providerId = userRepository.findById(actor.id())
                    .map(User::getCompanyId)
                    .orElse(null);
            if (providerId != null) user.setCompanyId(providerId);
        }
        user = userRepository.save(user);
        auditService.audit(actor, "USER_UPDATE", "User", user.getId(), "Email: " + user.getEmail(), http);
        return ResponseEntity.ok(UserResponse.from(user, companyName(user)));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deactivate(@PathVariable Long id, HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        if (actor.id().equals(id)) {
            throw ApiException.forbidden("You cannot deactivate your own account");
        }
        User user = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw ApiException.conflict("User is already deactivated");
        }
        user.setIsActive(false);
        user.setDeactivatedAt(java.time.LocalDateTime.now());
        userRepository.save(user);
        auditService.audit(actor, "USER_DEACTIVATE", "User", user.getId(), "Email: " + user.getEmail(), http);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    @Transactional
    public ResponseEntity<UserResponse> activate(@PathVariable Long id, HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        if (actor.id().equals(id)) {
            throw ApiException.forbidden("You cannot activate your own account");
        }
        User user = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        if (Boolean.TRUE.equals(user.getIsActive())) {
            throw ApiException.conflict("User is already active");
        }
        user.setIsActive(true);
        user.setDeactivatedAt(null);
        user = userRepository.save(user);
        auditService.audit(actor, "USER_ACTIVATE", "User", user.getId(), "Email: " + user.getEmail(), http);
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @DeleteMapping("/{id}/hard")
    @Transactional
    public ResponseEntity<Void> hardDelete(@PathVariable Long id,
                                           @Valid @RequestBody HardDeleteUserRequest req,
                                           HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        if (actor.id().equals(id)) {
            throw ApiException.forbidden("You cannot delete your own account");
        }
        User user = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        boolean eligible = user.getDeactivatedAt() != null
                && user.getDeactivatedAt().plusDays(7).isBefore(java.time.LocalDateTime.now());
        if (!eligible) {
            // Immediate delete (active user, or deactivated < 7 days) requires the acting admin's own password
            User actorRow = userRepository.findById(actor.id())
                    .orElseThrow(() -> ApiException.notFound("User"));
            if (!passwordEncoder.matches(req.password(), actorRow.getPasswordHash())) {
                throw ApiException.forbidden("Password confirmation failed");
            }
        }
        try {
            userRepository.delete(user);
            userRepository.flush();
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw ApiException.conflict("Cannot delete: this user still has related records (e.g. tasks, documents, audit history)");
        }
        auditService.audit(actor, "USER_HARD_DELETE", "User", user.getId(), "Email: " + user.getEmail(), http);
        return ResponseEntity.noContent().build();
    }
}
