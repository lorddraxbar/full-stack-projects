package com.secphils.controller;

import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
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
}
