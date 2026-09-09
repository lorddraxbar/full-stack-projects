package com.secphils.controller;

import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
import com.secphils.common.DropdownProtection;
import com.secphils.dto.DropdownCategoryResponse;
import com.secphils.entity.DropdownCategory;
import com.secphils.entity.DropdownValue;
import com.secphils.repository.DropdownCategoryRepository;
import com.secphils.repository.DropdownValueRepository;
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

@RestController
@RequestMapping("/api/v1/dropdowns")
public class DropdownController {

    private final DropdownCategoryRepository categoryRepository;
    private final DropdownValueRepository valueRepository;
    private final AuditService auditService;

    public DropdownController(DropdownCategoryRepository categoryRepository,
                              DropdownValueRepository valueRepository, AuditService auditService) {
        this.categoryRepository = categoryRepository;
        this.valueRepository = valueRepository;
        this.auditService = auditService;
    }

    /** Refuse writes to protected categories (V35): their row is documentation
     *  of a vocabulary enforced elsewhere (auth roles), not editable data. */
    private static void requireEditable(DropdownCategory category) {
        if (Boolean.TRUE.equals(category.getProtectedFlag())) {
            throw ApiException.badRequest(
                    "'" + category.getName() + "' is a protected category — its values are enforced by the system and cannot be edited here.");
        }
    }

    private static void requireEditableCode(DropdownCategory category, String code, String action) {
        requireEditable(category);
        if (DropdownProtection.isLockedValue(category.getName(), code)) {
            throw ApiException.badRequest(
                    "'" + code + "' is a protected " + category.getName() + " value — the portal's behavior keys on it, so it cannot be " + action + ". Rename its display label instead if you want a different name.");
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<DropdownCategoryResponse>> list() {
        return ResponseEntity.ok(
                categoryRepository.findAll().stream().map(DropdownCategoryResponse::from).toList());
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

    @PostMapping
    @Transactional
    public ResponseEntity<DropdownCategoryResponse> createCategory(
            @RequestBody Map<String, String> body, HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        String name = body.get("name");
        if (name == null || name.isBlank()) throw ApiException.badRequest("name is required");
        if (categoryRepository.findByName(name).isPresent()) {
            throw ApiException.conflict("Dropdown category already exists");
        }
        DropdownCategory category = new DropdownCategory();
        category.setName(name);
        category.setDescription(body.get("description"));
        category.setCreatedAt(LocalDateTime.now());
        category = categoryRepository.save(category);
        auditService.audit(actor, "DROPDOWN_CATEGORY_CREATE", "DropdownCategory", category.getId(),
                "Name: " + name, http);
        return ResponseEntity.status(HttpStatus.CREATED).body(DropdownCategoryResponse.from(category));
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
        requireEditable(category); // adding brand-new values stays allowed
        if (DropdownProtection.isLockedValue(category.getName(), value.trim())) {
            throw ApiException.badRequest("'" + value.trim() + "' is a reserved code in this category.");
        }
        DropdownValue dv = new DropdownValue();
        dv.setCategory(category);
        dv.setValue(value);
        dv.setDisplayLabel((String) body.getOrDefault("displayLabel", value));
        Object sortOrder = body.get("sortOrder");
        dv.setSortOrder(sortOrder != null ? Integer.valueOf(String.valueOf(sortOrder)) : 0);
        dv.setCreatedAt(LocalDateTime.now());
        dv = valueRepository.save(dv);
        auditService.audit(actor, "DROPDOWN_VALUE_CREATE", "DropdownValue", dv.getId(),
                "Category: " + categoryId + ", value: " + value, http);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(DropdownCategoryResponse.DropdownValueResponse.from(dv));
    }

    @PutMapping("/{categoryId}")
    @Transactional
    public ResponseEntity<DropdownCategoryResponse> updateCategory(
            @PathVariable Long categoryId, @RequestBody Map<String, String> body,
            HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        DropdownCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> ApiException.notFound("Dropdown category"));
        requireEditable(category);
        if (body.containsKey("name") && body.get("name") != null && !body.get("name").isBlank()
                && !body.get("name").equals(category.getName())
                && DropdownProtection.isWiredCategory(category.getName())) {
            throw ApiException.badRequest("'" + category.getName()
                    + "' is wired to portal forms by name — renaming it would silently disconnect them.");
        }
        if (body.containsKey("name") && body.get("name") != null && !body.get("name").isBlank()) {
            String name = body.get("name");
            Long selfId = category.getId();
            categoryRepository.findByName(name).ifPresent(existing -> {
                if (!existing.getId().equals(selfId)) {
                    throw ApiException.conflict("Dropdown category already exists");
                }
            });
            category.setName(name);
        }
        if (body.containsKey("description")) category.setDescription(body.get("description"));
        category = categoryRepository.save(category);
        auditService.audit(actor, "DROPDOWN_CATEGORY_UPDATE", "DropdownCategory", category.getId(),
                "Name: " + category.getName(), http);
        return ResponseEntity.ok(DropdownCategoryResponse.from(category));
    }

    @DeleteMapping("/{categoryId}")
    @Transactional
    public ResponseEntity<Void> deleteCategory(@PathVariable Long categoryId, HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        DropdownCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> ApiException.notFound("Dropdown category"));
        requireEditable(category);
        if (DropdownProtection.isWiredCategory(category.getName())) {
            throw ApiException.badRequest("'" + category.getName()
                    + "' is wired to portal forms — delete its unneeded values instead of the category.");
        }
        categoryRepository.delete(category); // cascades to its values
        auditService.audit(actor, "DROPDOWN_CATEGORY_DELETE", "DropdownCategory", categoryId,
                "Name: " + category.getName(), http);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/values/{valueId}")
    @Transactional
    public ResponseEntity<DropdownCategoryResponse.DropdownValueResponse> updateValue(
            @PathVariable Long valueId, @RequestBody Map<String, Object> body,
            HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        DropdownValue dv = valueRepository.findById(valueId)
                .orElseThrow(() -> ApiException.notFound("Dropdown value"));
        String value = (String) body.get("value");
        if (value != null && !value.isBlank()) {
            requireEditable(dv.getCategory());
            // Locked structural codes may never be renamed (displayLabel is
            // the rename surface); a non-locked value may not be renamed INTO
            // a locked code either.
            if (!dv.getValue().equals(value)) {
                if (DropdownProtection.isLockedValue(dv.getCategory().getName(), dv.getValue())) {
                    throw ApiException.badRequest("'" + dv.getValue()
                            + "' is a protected value — the portal's behavior keys on it, so its code cannot be renamed. Use the display label to change how it reads.");
                }
                if (DropdownProtection.isLockedValue(dv.getCategory().getName(), value)) {
                    throw ApiException.badRequest("'" + value + "' is a reserved code in this category.");
                }
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
        requireEditableCode(dv.getCategory(), dv.getValue(), "deleted");
        valueRepository.delete(dv);
        auditService.audit(actor, "DROPDOWN_VALUE_DELETE", "DropdownValue", valueId,
                "Category: " + dv.getCategory().getId() + ", value: " + dv.getValue(), http);
        return ResponseEntity.noContent().build();
    }
}
