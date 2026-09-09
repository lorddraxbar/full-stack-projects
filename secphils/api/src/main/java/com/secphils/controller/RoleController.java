package com.secphils.controller;

import com.secphils.dto.RoleResponse;
import com.secphils.entity.Permission;
import com.secphils.entity.Role;
import com.secphils.repository.RoleRepository;
import com.secphils.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only role inventory for Admin Panel > Company Settings (theater cut
 * 2026-09-09, Jaybar ruling).
 *
 * WHY READ-ONLY: nothing in the portal ever ENFORCED the stored
 * roles/role_permissions matrix. Authorization is hardcoded by design and by
 * security review (post-c799c16): SecurityConfig gates by users.role
 * (ROLE_CLIENT/USER/ADMIN — a closed set) and controllers re-check per row.
 * The matrix was an editable-looking copy of fiction: ticking or unticking a
 * permission changed a table nobody reads. The CRUD endpoints, the
 * GET /permissions vocabulary endpoint, and the role modal are therefore
 * REMOVED (git-restorable; tables + seed rows are kept dormant, same pattern
 * as the retired tasks feature). The panel now reports the truth: the three
 * fixed roles, where accounts are assigned (User Management), and that
 * capabilities live in code, not here.
 */
@RestController
@RequestMapping("/api/v1")
public class RoleController {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    public RoleController(RoleRepository roleRepository, UserRepository userRepository) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/roles")
    @Transactional(readOnly = true)
    public ResponseEntity<List<RoleResponse>> list() {
        return ResponseEntity.ok(roleRepository.findAll().stream().map(this::toResponse).toList());
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
