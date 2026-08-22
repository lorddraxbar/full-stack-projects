package com.secphils.controller;

import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
import com.secphils.dto.*;
import com.secphils.entity.User;
import com.secphils.repository.UserRepository;
import com.secphils.security.CurrentUser;
import com.secphils.security.JwtService;
import com.secphils.service.TwoFactorService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final TwoFactorService twoFactorService;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          JwtService jwtService, AuditService auditService,
                          TwoFactorService twoFactorService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.twoFactorService = twoFactorService;
    }

    @PostMapping("/login")
    @Transactional
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req,
                                               HttpServletRequest http) {
        User user = userRepository.findByEmail(req.email())
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                .orElseThrow(() -> ApiException.badRequest("Invalid email or password"));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw ApiException.badRequest("Invalid email or password");
        }
        auditService.audit(null, "USER_LOGIN", "User", user.getId(), "Email: " + user.getEmail(), http);
        // 2FA gate: never issue access tokens until the TOTP step succeeds.
        if (Boolean.TRUE.equals(user.getTwoFactorEnabled()) && user.getTwoFactorSecret() != null) {
            String pending = jwtService.generate(user.getId(), user.getEmail(), user.getRole(),
                    JwtService.TokenType.PENDING_2FA);
            return ResponseEntity.ok(new LoginResponse(true, pending, null, null, null, null,
                    UserResponse.from(user)));
        }
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);
        return ResponseEntity.ok(buildTokenResponse(user));
    }

    /** Second step of a 2FA-required login. */
    @PostMapping("/2fa/verify")
    @Transactional
    public ResponseEntity<LoginResponse> verify2fa(@Valid @RequestBody TwoFactorLoginRequest req,
                                                    HttpServletRequest http) {
        Claims claims;
        try {
            claims = jwtService.parse(req.pendingToken(), JwtService.TokenType.PENDING_2FA);
        } catch (Exception e) {
            throw ApiException.badRequest("Session expired. Please sign in again.");
        }
        User user = userRepository.findById(jwtService.userId(claims))
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                .orElseThrow(() -> ApiException.badRequest("User no longer active"));
        if (!Boolean.TRUE.equals(user.getTwoFactorEnabled()) || user.getTwoFactorSecret() == null
                || !twoFactorService.verify(user.getTwoFactorSecret(), req.code())) {
            auditService.audit(null, "USER_LOGIN_2FA_FAILED", "User", user.getId(), "Email: " + user.getEmail(), http);
            throw ApiException.badRequest("Invalid verification code");
        }
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);
        auditService.audit(null, "USER_LOGIN_2FA", "User", user.getId(), "Email: " + user.getEmail(), http);
        return ResponseEntity.ok(buildTokenResponse(user));
    }

    /** Begin enabling 2FA: returns the fresh secret + otpauth URI (not yet persisted). */
    @PostMapping("/2fa/enable")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, String>> enable2fa() {
        var me = CurrentUser.require();
        User user = userRepository.findById(me.id()).orElseThrow(() -> ApiException.notFound("User"));
        if (Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            throw ApiException.conflict("Two-factor authentication is already enabled");
        }
        String secret = twoFactorService.generateSecret();
        return ResponseEntity.ok(Map.of(
                "secret", secret,
                "otpauthUri", twoFactorService.otpauthUri(user.getEmail(), secret)));
    }

    /** Finish enabling 2FA with the user's current TOTP code (persists the secret). */
    @PostMapping("/2fa/verify-enable")
    @Transactional
    public ResponseEntity<Map<String, String>> verifyEnable2fa(@Valid @RequestBody VerifyTwoFactorEnableRequest req,
                                                                HttpServletRequest http) {
        var me = CurrentUser.require();
        User user = userRepository.findById(me.id()).orElseThrow(() -> ApiException.notFound("User"));
        if (!twoFactorService.verify(req.secret(), req.code())) {
            throw ApiException.badRequest("Invalid verification code");
        }
        user.setTwoFactorSecret(req.secret());
        user.setTwoFactorEnabled(true);
        userRepository.save(user);
        auditService.audit(me, "USER_2FA_ENABLED", "User", user.getId(), null, http);
        return ResponseEntity.ok(Map.of("enabled", "true"));
    }

    /** Disable 2FA, requiring the current TOTP code. */
    @PostMapping("/2fa/disable")
    @Transactional
    public ResponseEntity<Map<String, String>> disable2fa(@Valid @RequestBody TwoFactorRequest req,
                                                           HttpServletRequest http) {
        var me = CurrentUser.require();
        User user = userRepository.findById(me.id()).orElseThrow(() -> ApiException.notFound("User"));
        if (!Boolean.TRUE.equals(user.getTwoFactorEnabled()) || user.getTwoFactorSecret() == null) {
            throw ApiException.conflict("Two-factor authentication is not enabled");
        }
        if (!twoFactorService.verify(user.getTwoFactorSecret(), req.code())) {
            throw ApiException.badRequest("Invalid verification code");
        }
        user.setTwoFactorSecret(null);
        user.setTwoFactorEnabled(false);
        userRepository.save(user);
        auditService.audit(me, "USER_2FA_DISABLED", "User", user.getId(), null, http);
        return ResponseEntity.ok(Map.of("disabled", "true"));
    }

    /** Change password after verifying the current one. */
    @PostMapping("/change-password")
    @Transactional
    public ResponseEntity<Map<String, String>> changePassword(@Valid @RequestBody ChangePasswordRequest req,
                                                               HttpServletRequest http) {
        var me = CurrentUser.require();
        User user = userRepository.findById(me.id()).orElseThrow(() -> ApiException.notFound("User"));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        userRepository.save(user);
        auditService.audit(me, "USER_CHANGE_PASSWORD", "User", user.getId(), null, http);
        return ResponseEntity.ok(Map.of("message", "Password updated"));
    }

    @PostMapping("/refresh")
    @Transactional
    public ResponseEntity<TokenResponse> refresh(@RequestBody Map<String, String> body) {
        String token = body.get("refreshToken");
        if (token == null || token.isBlank()) {
            throw ApiException.badRequest("refreshToken is required");
        }
        Claims claims;
        try {
            claims = jwtService.parse(token, JwtService.TokenType.REFRESH);
        } catch (Exception e) {
            throw ApiException.badRequest("Invalid or expired refresh token");
        }
        User user = userRepository.findById(jwtService.userId(claims))
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                .orElseThrow(() -> ApiException.badRequest("User no longer active"));
        String access = jwtService.generate(user.getId(), user.getEmail(), user.getRole(), JwtService.TokenType.ACCESS);
        String newRefresh = jwtService.generate(user.getId(), user.getEmail(), user.getRole(), JwtService.TokenType.REFRESH);
        return ResponseEntity.ok(TokenResponse.of(access, newRefresh, 900, UserResponse.from(user)));
    }

    @PostMapping("/logout")
    @Transactional
    public ResponseEntity<Map<String, String>> logout() {
        // Stateless JWT: client discards tokens. Hook for a denylist goes here if needed.
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    @PostMapping("/sso/google")
    @Transactional
    public ResponseEntity<LoginResponse> ssoGoogle(@Valid @RequestBody SsoCallbackRequest req,
                                                   HttpServletRequest http) {
        return ssoLogin(req, "GOOGLE", http);
    }

    @PostMapping("/sso/microsoft")
    @Transactional
    public ResponseEntity<LoginResponse> ssoMicrosoft(@Valid @RequestBody SsoCallbackRequest req,
                                                      HttpServletRequest http) {
        return ssoLogin(req, "MICROSOFT", http);
    }

    @PostMapping("/sso/linkedin")
    @Transactional
    public ResponseEntity<LoginResponse> ssoLinkedIn(@Valid @RequestBody SsoCallbackRequest req,
                                                     HttpServletRequest http) {
        return ssoLogin(req, "LINKEDIN", http);
    }

    private ResponseEntity<LoginResponse> ssoLogin(SsoCallbackRequest req, String provider, HttpServletRequest http) {
        // Stub: in production, verify the provider token/ID here before trusting the identity.
        User user = userRepository.findByEmail(req.email()).orElseGet(() -> {
            User u = new User();
            u.setEmail(req.email());
            u.setFirstName(req.firstName());
            u.setLastName(req.lastName());
            u.setRole("CLIENT");
            u.setIsActive(true);
            return u;
        });
        user = userRepository.save(user);
        auditService.audit(null, "USER_SSO_LOGIN", "User", user.getId(), "Provider: " + provider, http);
        return ResponseEntity.ok(buildTokenResponse(user));
    }

    private LoginResponse buildTokenResponse(User user) {
        String access = jwtService.generate(user.getId(), user.getEmail(), user.getRole(), JwtService.TokenType.ACCESS);
        String refresh = jwtService.generate(user.getId(), user.getEmail(), user.getRole(), JwtService.TokenType.REFRESH);
        return new LoginResponse(false, null, access, refresh, "Bearer", 900L, UserResponse.from(user));
    }
}