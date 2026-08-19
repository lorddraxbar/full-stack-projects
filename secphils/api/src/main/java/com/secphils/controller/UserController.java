package com.secphils.controller;

import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
import com.secphils.dto.HardDeleteUserRequest;
import com.secphils.dto.SetPasswordRequest;
import com.secphils.dto.UpdateUserRequest;
import com.secphils.dto.UserResponse;
import com.secphils.entity.User;
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
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final MailService mailService;
    private final String inviteBaseUrl;
    private final Duration inviteTtl;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          AuditService auditService, MailService mailService,
                          @Value("${app.invite.base-url}") String inviteBaseUrl,
                          @Value("${app.invite.token-ttl:24h}") Duration inviteTtl) {
        this.userRepository = userRepository;
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
        return ResponseEntity.ok(UserResponse.from(user));
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
        if (req.password() != null && !req.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(req.password()));
        }
        if (req.firstName() != null && !req.firstName().isBlank()) user.setFirstName(req.firstName());
        if (req.lastName() != null && !req.lastName().isBlank()) user.setLastName(req.lastName());
        // self cannot change own role or active status
        user = userRepository.save(user);
        auditService.audit(me, "USER_UPDATE_SELF", "User", user.getId(), null, http);
        return ResponseEntity.ok(UserResponse.from(user));
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<UserResponse>> list() {
        return ResponseEntity.ok(userRepository.findAll().stream().map(UserResponse::from).toList());
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
        User user = new User();
        user.setEmail(req.email());
        user.setFirstName(req.firstName());
        user.setLastName(req.lastName());
        user.setRole(req.role() != null ? req.role() : "CLIENT");
        // New users start inactive — they activate by setting their own password via the invite link
        user.setIsActive(false);
        user = userRepository.save(user);
        issueInvite(user, actor, http);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
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
        String link = inviteBaseUrl + "/auth/set-password?token=" + token;
        mailService.sendHtml(user.getEmail(), "Set your SECPhils Portal password",
                mailService.inviteEmail(user.getFirstName(), user.getFullName(), link));
        auditService.audit(actor, "USER_INVITE_SENT", "User", user.getId(), "Email: " + user.getEmail(), http);
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
        user = userRepository.save(user);
        auditService.audit(actor, "USER_UPDATE", "User", user.getId(), "Email: " + user.getEmail(), http);
        return ResponseEntity.ok(UserResponse.from(user));
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
