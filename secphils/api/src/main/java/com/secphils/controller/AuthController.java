package com.secphils.controller;

import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
import com.secphils.dto.*;
import com.secphils.entity.User;
import com.secphils.repository.UserRepository;
import com.secphils.security.CurrentUser;
import com.secphils.security.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
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

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          JwtService jwtService, AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    @PostMapping("/register")
    @Transactional
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest req,
                                                 HttpServletRequest http) {
        if (userRepository.findByEmail(req.email()).isPresent()) {
            throw ApiException.conflict("A user with this email already exists");
        }
        User user = new User();
        user.setEmail(req.email());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setFirstName(req.firstName());
        user.setLastName(req.lastName());
        user.setRole(req.role());
        user.setIsActive(true);
        user = userRepository.save(user);
        auditService.audit(null, "USER_REGISTER", "User", user.getId(), "Email: " + user.getEmail(), http);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
    }

    @PostMapping("/login")
    @Transactional
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest req,
                                               HttpServletRequest http) {
        User user = userRepository.findByEmail(req.email())
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                .orElseThrow(() -> ApiException.badRequest("Invalid email or password"));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw ApiException.badRequest("Invalid email or password");
        }
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);
        String access = jwtService.generate(user.getId(), user.getEmail(), user.getRole(), JwtService.TokenType.ACCESS);
        String refresh = jwtService.generate(user.getId(), user.getEmail(), user.getRole(), JwtService.TokenType.REFRESH);
        auditService.audit(null, "USER_LOGIN", "User", user.getId(), "Email: " + user.getEmail(), http);
        return ResponseEntity.ok(TokenResponse.of(access, refresh, 900, UserResponse.from(user)));
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
    public ResponseEntity<TokenResponse> ssoGoogle(@Valid @RequestBody SsoCallbackRequest req,
                                                   HttpServletRequest http) {
        return ssoLogin(req, "GOOGLE", http);
    }

    @PostMapping("/sso/microsoft")
    @Transactional
    public ResponseEntity<TokenResponse> ssoMicrosoft(@Valid @RequestBody SsoCallbackRequest req,
                                                      HttpServletRequest http) {
        return ssoLogin(req, "MICROSOFT", http);
    }

    @PostMapping("/sso/linkedin")
    @Transactional
    public ResponseEntity<TokenResponse> ssoLinkedIn(@Valid @RequestBody SsoCallbackRequest req,
                                                     HttpServletRequest http) {
        return ssoLogin(req, "LINKEDIN", http);
    }

    private ResponseEntity<TokenResponse> ssoLogin(SsoCallbackRequest req, String provider, HttpServletRequest http) {
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
        String access = jwtService.generate(user.getId(), user.getEmail(), user.getRole(), JwtService.TokenType.ACCESS);
        String refresh = jwtService.generate(user.getId(), user.getEmail(), user.getRole(), JwtService.TokenType.REFRESH);
        return ResponseEntity.ok(TokenResponse.of(access, refresh, 900, UserResponse.from(user)));
    }

    @PostMapping("/2fa/enable")
    @Transactional
    public ResponseEntity<Map<String, String>> enable2fa() {
        // Stub: 2FA storage (TOTP secret / device key) is not in the Phase 2 schema.
        throw new ApiException(HttpStatus.NOT_IMPLEMENTED, "2FA enablement is not implemented yet");
    }

    @PostMapping("/2fa/disable")
    @Transactional
    public ResponseEntity<Map<String, String>> disable2fa() {
        throw new ApiException(HttpStatus.NOT_IMPLEMENTED, "2FA disablement is not implemented yet");
    }

    @PostMapping("/2fa/verify")
    @Transactional
    public ResponseEntity<Map<String, String>> verify2fa(@Valid @RequestBody TwoFactorRequest req) {
        throw new ApiException(HttpStatus.NOT_IMPLEMENTED, "2FA verification is not implemented yet");
    }
}
