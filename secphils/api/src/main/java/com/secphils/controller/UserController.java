package com.secphils.controller;

import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
import com.secphils.dto.UpdateUserRequest;
import com.secphils.dto.UserResponse;
import com.secphils.entity.User;
import com.secphils.repository.UserRepository;
import com.secphils.security.CurrentUser;
import com.secphils.security.AuthUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder, AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
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
        user.setIsActive(req.isActive() != null ? req.isActive() : true);
        if (req.password() != null && !req.password().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(req.password()));
        }
        user = userRepository.save(user);
        auditService.audit(actor, "USER_CREATE", "User", user.getId(), "Email: " + user.getEmail(), http);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponse.from(user));
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
        User user = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        user.setIsActive(false);
        userRepository.save(user);
        auditService.audit(actor, "USER_DEACTIVATE", "User", user.getId(), "Email: " + user.getEmail(), http);
        return ResponseEntity.noContent().build();
    }
}
