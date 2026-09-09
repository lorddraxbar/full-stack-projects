package com.secphils.controller;

import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
import com.secphils.common.DropdownProtection;
import com.secphils.dto.DropdownCategoryResponse;
import com.secphils.entity.DropdownCategory;
import com.secphils.entity.DropdownValue;
import com.secphils.repository.AnnouncementRepository;
import com.secphils.repository.DropdownCategoryRepository;
import com.secphils.repository.DropdownValueRepository;
import com.secphils.repository.ProjectRepository;
import com.secphils.security.AuthUser;
import com.secphils.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Vocabulary the wired portal forms read (Admin -> Project Config).
 *
 * CATEGORY SET IS STRUCTURE, NOT DATA: the set is fixed in migrations and the
 * forms enumerate it by name. A category an admin invented would have no
 * form to read it (every dropdown is wired to a specific category), so there
 * are deliberately NO category create/update/delete endpoints. The
 * user_role category is protectedCategory — enforced by auth, not by data —
 * and is hidden from the panel entirely.
 *
 * VALUE RULES per category:
 *   project_status / announcement_category: values are vocabulary — admins
 *     may add new codes and rename labels; deleting a code still IN USE by
 *     a project/announcement is refused (it would orphan the row's badge and
 *     drop it from its editor's options).
 *   audience: binary visibility switch (frozen) — fan-out ignores it and the
 *     client list filter only understands PROJECT/COMPANY, so a third code
 *     would silently hide announcements from every client. Label renames are
 *     the only edit.
 *   protected codes (5 statuses, both audiences): code fixed; label free.
 */
@RestController
@RequestMapping("/api/v1/dropdowns")
public class DropdownController {

    private final DropdownCategoryRepository categoryRepository;
    private final DropdownValueRepository valueRepository;
    private final ProjectRepository projectRepository;
    private final AnnouncementRepository announcementRepository;
    private final AuditService auditService;

    public DropdownController(DropdownCategoryRepository categoryRepository,
                              DropdownValueRepository valueRepository,
                              ProjectRepository projectRepository,
                              AnnouncementRepository announcementRepository,
                              AuditService auditService) {
        this.categoryRepository = categoryRepository;
        this.valueRepository = valueRepository;
        this.projectRepository = projectRepository;
        this.announcementRepository = announcementRepository;
        this.auditService = auditService;
    }

    /** Refuse writes to protected categories (V35): their vocabulary is
     *  enforced elsewhere (auth roles); they are not editable data. */
    private static void requireEditable(DropdownCategory category) {
        if (Boolean.TRUE.equals(category.getProtectedFlag())) {
            throw ApiException.badRequest(
                    "'" + category.getName() + "' is a protected category — its values are enforced by the system and cannot be edited here.");
        }
    }

    /** Frozen categories (audience) accept displayLabel edits ONLY. */
    private static void requireNotFrozen(DropdownCategory category) {
        requireEditable(category);
        if (DropdownProtection.isFrozenValues(category.getName())) {
            throw ApiException.badRequest("'" + category.getName()
                    + "' is a fixed two-value switch (Project / Company-wide) — the portal's visibility logic only understands those codes. You may rename the labels.");
        }
    }

    private static boolean isProtectedCode(DropdownCategory category, String code) {
        return DropdownProtection.isLockedValue(category.getName(), code);
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<DropdownCategoryResponse>> list() {
        // Protected categories (user_role) are hidden: nothing in the portal
        // reads them and the panel must not offer controls it must refuse.
        return ResponseEntity.ok(
                categoryRepository.findAll().stream()
                        .filter(c -> !Boolean.TRUE.equals(c.getProtectedFlag()))
                        .map(DropdownCategoryResponse::from).toList());
    }

    @GetMapping("/values")
    @Transactional(readOnly = true)
    public ResponseEntity<List<DropdownCategoryResponse.DropdownValueResponse>> listValues(
            @RequestParam(required = false) Long categoryId) {
        List<DropdownValue> values = (categoryId != null)
                ? valueRepository.findByCategoryIdOrderBySortOrderAsc(categoryId)
                : valueRepository.findAll();
        return ResponseEntity.ok(values.stream()
                .map(DropdownCategoryResponse.DropdownValueResponse::from).toList());
    }

    @PostMapping("/values")
    @Transactional
    public ResponseEntity<DropdownCategoryResponse.DropdownValueResponse> createValue(
            @RequestBody Map<String, Object> body, HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        Object categoryIdRaw = body.get("categoryId");
        String value = (String) body.get("value");
        if (categoryIdRaw == null || value == null || value.isBlank()) {
            throw ApiException.badRequest("categoryId and value are required");
        }
        Long categoryId = Long.valueOf(String.valueOf(categoryIdRaw));
        DropdownCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> ApiException.notFound("Dropdown category"));
        requireNotFrozen(category);
        if (isProtectedCode(category, value.trim())) {
            throw ApiException.badRequest("'" + value.trim() + "' is a reserved code in this category.");
        }
        if (valueRepository.findByCategoryIdAndValue(categoryId, value.trim()).isPresent()) {
            throw ApiException.conflict("This value already exists in the category");
        }
        DropdownValue dv = new DropdownValue();
        dv.setCategory(category);
        dv.setValue(value.trim());
        dv.setDisplayLabel((String) body.getOrDefault("displayLabel", value.trim()));
        Object sortOrder = body.get("sortOrder");
        dv.setSortOrder(sortOrder != null ? Integer.valueOf(String.valueOf(sortOrder)) : 0);
        dv.setCreatedAt(LocalDateTime.now());
        dv = valueRepository.save(dv);
        auditService.audit(actor, "DROPDOWN_VALUE_CREATE", "DropdownValue", dv.getId(),
                "Category: " + categoryId + ", value: " + value, http);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(DropdownCategoryResponse.DropdownValueResponse.from(dv));
    }

    @PutMapping("/values/{valueId}")
    @Transactional
    public ResponseEntity<DropdownCategoryResponse.DropdownValueResponse> updateValue(
            @PathVariable Long valueId, @RequestBody Map<String, Object> body,
            HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        DropdownValue dv = valueRepository.findById(valueId)
                .orElseThrow(() -> ApiException.notFound("Dropdown value"));
        // Guard BEFORE branching on the payload: label-only PUTs must hit it too
        // (a label-only write to a protected category used to slip past).
        requireEditable(dv.getCategory());
        String value = (String) body.get("value");
        if (value != null && !value.isBlank() && !dv.getValue().equals(value)) {
            // code renames: never on protected codes; never at all on frozen
            // categories; never INTO a protected code.
            if (isProtectedCode(dv.getCategory(), dv.getValue())) {
                throw ApiException.badRequest("'" + dv.getValue()
                        + "' is a protected value — the portal's behavior keys on it, so its code cannot be renamed. Use the display label to change how it reads.");
            }
            if (DropdownProtection.isFrozenValues(dv.getCategory().getName())) {
                throw ApiException.badRequest("'" + dv.getCategory().getName()
                        + "' values keep their fixed codes; rename the display label instead.");
            }
            if (isProtectedCode(dv.getCategory(), value)) {
                throw ApiException.badRequest("'" + value + "' is a reserved code in this category.");
            }
            Long selfId = dv.getId();
            Long categoryId = dv.getCategory().getId();
            valueRepository.findByCategoryIdAndValue(categoryId, value)
                    .ifPresent(existing -> {
                        if (!existing.getId().equals(selfId)) {
                            throw ApiException.conflict("This value already exists in the category");
                        }
                    });
            dv.setValue(value);
        }
        if (body.get("displayLabel") != null) dv.setDisplayLabel((String) body.get("displayLabel"));
        if (body.get("sortOrder") != null) {
            dv.setSortOrder(Integer.valueOf(String.valueOf(body.get("sortOrder"))));
        }
        dv = valueRepository.save(dv);
        auditService.audit(actor, "DROPDOWN_VALUE_UPDATE", "DropdownValue", dv.getId(),
                "Category: " + dv.getCategory().getId() + ", value: " + dv.getValue(), http);
        return ResponseEntity.ok(DropdownCategoryResponse.DropdownValueResponse.from(dv));
    }

    @DeleteMapping("/values/{valueId}")
    @Transactional
    public ResponseEntity<Void> deleteValue(@PathVariable Long valueId, HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        DropdownValue dv = valueRepository.findById(valueId)
                .orElseThrow(() -> ApiException.notFound("Dropdown value"));
        DropdownCategory cat = dv.getCategory();
        requireEditable(cat);
        if (isProtectedCode(cat, dv.getValue())) {
            throw ApiException.badRequest("'" + dv.getValue()
                    + "' is a protected " + cat.getName() + " value — the portal's behavior keys on it, so it cannot be deleted. Rename its display label instead if you want a different name.");
        }
        if (DropdownProtection.isFrozenValues(cat.getName())) {
            throw ApiException.badRequest("'" + cat.getName()
                    + "' is a fixed two-value switch; its values cannot be deleted.");
        }
        // Deleting a code still in use would orphan the row's badge and drop it
        // from the editor's options — refuse; hide it with a label rename instead.
        if ("project_status".equals(cat.getName()) && projectRepository.existsByStatus(dv.getValue())) {
            throw ApiException.badRequest("Projects still use '" + dv.getValue()
                    + "' — move them to another status before deleting it.");
        }
        if ("announcement_category".equals(cat.getName()) && announcementRepository.existsByCategory(dv.getValue())) {
            throw ApiException.badRequest("Announcements still use '" + dv.getValue()
                    + "' — republish them under another category before deleting it.");
        }
        valueRepository.delete(dv);
        auditService.audit(actor, "DROPDOWN_VALUE_DELETE", "DropdownValue", valueId,
                "Category: " + cat.getId() + ", value: " + dv.getValue(), http);
        return ResponseEntity.noContent().build();
    }
}
