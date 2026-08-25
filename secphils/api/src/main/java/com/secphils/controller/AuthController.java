package com.secphils.controller;

import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
import com.secphils.dto.*;
import com.secphils.entity.SystemSettings;
import com.secphils.entity.User;
import com.secphils.repository.SystemSettingsRepository;
import com.secphils.repository.UserRepository;
import com.secphils.security.CurrentUser;
import com.secphils.security.JwtService;
import com.secphils.service.SsoService;
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
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;
    private final TwoFactorService twoFactorService;
    private final SsoService ssoService;
    private final SystemSettingsRepository settingsRepository;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          JwtService jwtService, AuditService auditService,
                          TwoFactorService twoFactorService, SsoService ssoService,
                          SystemSettingsRepository settingsRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
        this.twoFactorService = twoFactorService;
        this.ssoService = ssoService;
        this.settingsRepository = settingsRepository;
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

    // ------------------------------------------------------------------
    // Google SSO — OAuth 2.0 authorization-code flow (id_token + JWKS).
    // Identities are established exclusively from Google-verified claims;
    // the previous "trust a client-submitted email" stub is gone.
    // ------------------------------------------------------------------

    /** Lets the login page decide whether to render the Google button. */
    @GetMapping("/sso/status")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> ssoStatus() {
        GoogleSsoConfig cfg = currentSsoConfig();
        boolean ready = cfg.enabled
                && cfg.clientId != null && !cfg.clientId.isBlank()
                && cfg.clientSecret != null && !cfg.clientSecret.isBlank()
                && cfg.redirectUri != null && !cfg.redirectUri.isBlank();
        return ResponseEntity.ok(Map.of("googleEnabled", cfg.enabled, "googleConfigured", ready));
    }

    /**
     * Step 1: browser asks us where to send the user. We mint a signed
     * state nonce (CSRF / login-forgery protection) and return Google's
     * authorization URL for the frontend to redirect to.
     */
    @PostMapping("/sso/google/authorize")
    @Transactional(readOnly = true)
    public ResponseEntity<GoogleSsoAuthorizeResponse> ssoGoogleAuthorize() {
        GoogleSsoConfig cfg = currentSsoConfig();
        if (!cfg.enabled || cfg.clientId == null || cfg.clientId.isBlank()) {
            throw ApiException.notFound("Google sign-in is not enabled");
        }
        if (cfg.clientSecret == null || cfg.clientSecret.isBlank()) {
            throw ApiException.badRequest("Google SSO is misconfigured (client secret missing)");
        }
        if (cfg.redirectUri == null || cfg.redirectUri.isBlank()) {
            throw ApiException.badRequest("Google SSO is misconfigured (redirect URI missing)");
        }
        String state = jwtService.signSsoState(UUID.randomUUID().toString());
        return ResponseEntity.ok(new GoogleSsoAuthorizeResponse(ssoService.buildAuthorizeUrl(cfg, state)));
    }

    /**
     * Step 2: Google redirected the browser back with code + state. The
     * callback page posts both here; we verify the signed state, exchange
     * the code with Google, verify the id_token via JWKS, then sign the
     * user in (auto-provisioning new users as CLIENT, like invites).
     */
    @PostMapping("/sso/google/callback")
    @Transactional
    public ResponseEntity<LoginResponse> ssoGoogleCallback(@Valid @RequestBody GoogleSsoCallbackRequest req,
                                                           HttpServletRequest http) {
        GoogleSsoConfig cfg = currentSsoConfig();
        if (!cfg.enabled) {
            throw ApiException.badRequest("Google sign-in is disabled");
        }
        if (!jwtService.verifySsoState(req.state())) {
            auditService.audit(null, "SSO_STATE_MISMATCH", "User", null,
                    "state: " + String.valueOf(req.state()).substring(0, Math.min(24, String.valueOf(req.state()).length())), http);
            throw ApiException.badRequest("Invalid or expired SSO session. Please try again.");
        }

        Map<String, String> claims;
        try {
            claims = ssoService.exchangeCode(cfg, req.code());
        } catch (Exception e) {
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            auditService.audit(null, "SSO_LOGIN_FAILED", "User", null,
                    "provider: GOOGLE, reason: " + reason, http);
            throw ApiException.badRequest(reason);
        }

        String email = claims.get("email");
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User u = new User();
            u.setEmail(email);
            u.setFirstName(claims.getOrDefault("given_name", ""));
            u.setLastName(claims.getOrDefault("family_name", ""));
            u.setRole("CLIENT");
            u.setIsActive(true);
            return u;
        });
        // Keep the profile fresh if Google has it and the row is stale.
        if (user.getFirstName() == null || user.getFirstName().isBlank()) {
            user.setFirstName(claims.getOrDefault("given_name", ""));
            user.setLastName(claims.getOrDefault("family_name", ""));
        }
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);
        auditService.audit(null, "USER_SSO_LOGIN", "User", user.getId(), "Provider: GOOGLE", http);
        return ResponseEntity.ok(buildTokenResponse(user));
    }

    private GoogleSsoConfig currentSsoConfig() {
        return settingsRepository.findAll().stream().findFirst()
                .map(s -> SsoService.fromJson(s.getGoogleSso()))
                .orElseGet(GoogleSsoConfig::new);
    }

    private LoginResponse buildTokenResponse(User user) {
        String access = jwtService.generate(user.getId(), user.getEmail(), user.getRole(), JwtService.TokenType.ACCESS);
        String refresh = jwtService.generate(user.getId(), user.getEmail(), user.getRole(), JwtService.TokenType.REFRESH);
        return new LoginResponse(false, null, access, refresh, "Bearer", 900L, UserResponse.from(user));
    }
}