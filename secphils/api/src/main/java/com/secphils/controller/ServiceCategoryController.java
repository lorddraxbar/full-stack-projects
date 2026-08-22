package com.secphils.controller;

import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
import com.secphils.dto.ServiceCategoryRequest;
import com.secphils.dto.ServiceCategoryResponse;
import com.secphils.entity.Service;
import com.secphils.entity.ServiceCategory;
import com.secphils.repository.ServiceCategoryRepository;
import com.secphils.repository.ServiceRepository;
import com.secphils.security.AuthUser;
import com.secphils.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/service-categories")
public class ServiceCategoryController {

    private final ServiceCategoryRepository categoryRepository;
    private final ServiceRepository serviceRepository;
    private final AuditService auditService;

    public ServiceCategoryController(ServiceCategoryRepository categoryRepository,
                                     ServiceRepository serviceRepository,
                                     AuditService auditService) {
        this.categoryRepository = categoryRepository;
        this.serviceRepository = serviceRepository;
        this.auditService = auditService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<ServiceCategoryResponse>> list() {
        List<ServiceCategory> items = categoryRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(ServiceCategory::getSortOrder,
                                java.util.Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ServiceCategory::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return ResponseEntity.ok(items.stream().map(c -> ServiceCategoryResponse.from(c, countServices(c.getId()))).toList());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ServiceCategoryResponse> create(@Valid @RequestBody ServiceCategoryRequest req,
                                                          HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        if (categoryRepository.existsByName(req.name())) {
            throw ApiException.conflict("A category named \"" + req.name() + "\" already exists");
        }
        ServiceCategory c = new ServiceCategory();
        c.setName(req.name().trim());
        if (req.icon() != null && !req.icon().isBlank()) c.setIcon(req.icon());
        if (req.sortOrder() != null) c.setSortOrder(req.sortOrder());
        c = categoryRepository.save(c);
        auditService.audit(actor, "SERVICE_CATEGORY_CREATE", "ServiceCategory", c.getId(), "Name: " + c.getName(), http);
        return ResponseEntity.status(HttpStatus.CREATED).body(ServiceCategoryResponse.from(c, 0));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<ServiceCategoryResponse> update(@PathVariable Long id,
                                                          @Valid @RequestBody ServiceCategoryRequest req,
                                                          HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        ServiceCategory c = categoryRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Service category"));
        String newName = req.name().trim();
        if (!newName.equalsIgnoreCase(c.getName())) {
            categoryRepository.findByName(newName)
                    .filter(other -> !other.getId().equals(id))
                    .ifPresent(other -> {
                        throw ApiException.conflict("A category named \"" + newName + "\" already exists");
                    });
        }
        String oldName = c.getName();
        c.setName(newName);
        if (req.icon() != null && !req.icon().isBlank()) c.setIcon(req.icon());
        if (req.sortOrder() != null) c.setSortOrder(req.sortOrder());
        c = categoryRepository.save(c);
        auditService.audit(actor, "SERVICE_CATEGORY_UPDATE", "ServiceCategory", c.getId(),
                "Name: " + oldName + " -> " + c.getName(), http);
        return ResponseEntity.ok(ServiceCategoryResponse.from(c, countServices(c.getId())));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        ServiceCategory c = categoryRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Service category"));
        if (countServices(id) > 0) {
            throw ApiException.conflict(
                    "Cannot delete: " + c.getName() + " still has services. Move or delete those services first.");
        }
        categoryRepository.delete(c);
        auditService.audit(actor, "SERVICE_CATEGORY_DELETE", "ServiceCategory", id, "Name: " + c.getName(), http);
        return ResponseEntity.noContent().build();
    }

    private int countServices(Long categoryId) {
        return (int) serviceRepository.findAll().stream()
                .filter(s -> s.getCategory() != null && s.getCategory().getId().equals(categoryId))
                .count();
    }
}
