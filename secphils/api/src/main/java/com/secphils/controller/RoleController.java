package com.secphils.controller;

import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
import com.secphils.dto.RoleRequest;
import com.secphils.dto.RoleResponse;
import com.secphils.entity.Permission;
import com.secphils.entity.Role;
import com.secphils.repository.PermissionRepository;
import com.secphils.repository.RoleRepository;
import com.secphils.repository.UserRepository;
import com.secphils.security.AuthUser;
import com.secphils.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

/**
 * Role & permission management for the Admin Panel > Company Settings tab.
 * SecurityConfig already restricts /api/v1/roles/** and /api/v1/permissions/** to ADMIN.
 */
@RestController
@RequestMapping("/api/v1")
public class RoleController {

    private static final Set<String> VALID_USER_TYPES = Set.of("CLIENT", "USER", "ADMIN");

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public RoleController(RoleRepository roleRepository, PermissionRepository permissionRepository,
                          UserRepository userRepository, AuditService auditService) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    @GetMapping("/roles")
    @Transactional(readOnly = true)
    public ResponseEntity<List<RoleResponse>> list() {
        return ResponseEntity.ok(roleRepository.findAll().stream().map(this::toResponse).toList());
    }

    @PostMapping("/roles")
    @Transactional
    public ResponseEntity<RoleResponse> create(@Valid @RequestBody RoleRequest req, HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        if (roleRepository.existsByName(req.name())) {
            throw ApiException.conflict("A role with this name already exists");
        }
        Role role = new Role();
        role.setName(req.name());
        role.setUserType(validateUserType(req.userType()));
        role.setDescription(req.description());
        role.setPermissions(loadPermissions(req.permissionIds()));
        role = roleRepository.save(role);
        auditService.audit(actor, "ROLE_CREATE", "Role", role.getId(), "Name: " + role.getName(), http);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(role));
    }

    @PutMapping("/roles/{id}")
    @Transactional
    public ResponseEntity<RoleResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody RoleRequest req,
                                               HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        Role role = roleRepository.findById(id).orElseThrow(() -> ApiException.notFound("Role"));
        // System roles keep their name; everything else is customizable.
        if (!Boolean.TRUE.equals(role.getIsSystem())) {
            roleRepository.findByName(req.name())
                    .filter(r -> !r.getId().equals(id))
                    .ifPresent(r -> {
                        throw ApiException.conflict("A role with this name already exists");
                    });
            role.setName(req.name());
        }
        role.setUserType(validateUserType(req.userType()));
        role.setDescription(req.description());
        role.setPermissions(loadPermissions(req.permissionIds()));
        role = roleRepository.save(role);
        auditService.audit(actor, "ROLE_UPDATE", "Role", role.getId(), "Name: " + role.getName(), http);
        return ResponseEntity.ok(toResponse(role));
    }

    @DeleteMapping("/roles/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        Role role = roleRepository.findById(id).orElseThrow(() -> ApiException.notFound("Role"));
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw ApiException.forbidden("System roles cannot be deleted");
        }
        long assigned = userRepository.countByRole(role.getName());
        if (assigned > 0) {
            throw ApiException.conflict("This role is assigned to " + assigned
                    + " account" + (assigned == 1 ? "" : "s") + " and cannot be deleted");
        }
        roleRepository.delete(role);
        auditService.audit(actor, "ROLE_DELETE", "Role", id, "Name: " + role.getName(), http);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/permissions")
    @Transactional(readOnly = true)
    public ResponseEntity<List<com.secphils.dto.PermissionResponse>> permissions() {
        return ResponseEntity.ok(permissionRepository.findAll().stream()
                .map(p -> new com.secphils.dto.PermissionResponse(p.getId(), p.getName(), p.getDescription()))
                .toList());
    }

    private String validateUserType(String userType) {
        if (userType == null || !VALID_USER_TYPES.contains(userType)) {
            throw ApiException.badRequest("userType must be one of: CLIENT, USER, ADMIN");
        }
        return userType;
    }

    private Set<Permission> loadPermissions(List<Long> permissionIds) {
        if (permissionIds == null) return java.util.Collections.emptySet();
        return new java.util.HashSet<>(permissionRepository.findAllById(permissionIds));
    }

    private RoleResponse toResponse(Role role) {
        List<Long> permissionIds = role.getPermissions().stream()
                .map(Permission::getId)
                .sorted()
                .collect(Collectors.toList());
        long assigned = userRepository.countByRole(role.getName());
        return new RoleResponse(role.getId(), role.getName(), role.getDescription(),
                role.getUserType(), role.getIsSystem(), permissionIds, assigned);
    }
}
