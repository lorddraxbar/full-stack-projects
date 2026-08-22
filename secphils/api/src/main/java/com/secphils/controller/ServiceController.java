package com.secphils.controller;

import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
import com.secphils.dto.HardDeleteUserRequest;
import com.secphils.dto.ServiceRequest;
import com.secphils.dto.ServiceResponse;
import com.secphils.entity.Service;
import com.secphils.entity.ServiceCategory;
import com.secphils.entity.User;
import com.secphils.repository.ServiceCategoryRepository;
import com.secphils.repository.ServiceRepository;
import com.secphils.repository.UserRepository;
import com.secphils.security.AuthUser;
import com.secphils.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@RestController
@RequestMapping("/api/v1/services")
public class ServiceController {

    private final ServiceRepository serviceRepository;
    private final ServiceCategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public ServiceController(ServiceRepository serviceRepository,
                             ServiceCategoryRepository categoryRepository,
                             UserRepository userRepository,
                             PasswordEncoder passwordEncoder,
                             AuditService auditService) {
        this.serviceRepository = serviceRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    private AuthUser requireActor() {
        return CurrentUser.require();
    }

    private void audit(AuthUser actor, String action, Long serviceId) {
        if (actor == null) return;
        try {
            HttpServletRequest req = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
            auditService.audit(actor, action, "Service", serviceId, "", req);
        } catch (Exception ignored) {
        }
    }

    @GetMapping
    public List<ServiceResponse> list() {
        return serviceRepository.findAll().stream()
                .sorted((a, b) -> {
                    int ca = a.getCategory() != null ? a.getCategory().getSortOrder() : Integer.MAX_VALUE;
                    int cb = b.getCategory() != null ? b.getCategory().getSortOrder() : Integer.MAX_VALUE;
                    if (ca != cb) return Integer.compare(ca, cb);
                    int sa = a.getSortOrder() != null ? a.getSortOrder() : Integer.MAX_VALUE;
                    int sb = b.getSortOrder() != null ? b.getSortOrder() : Integer.MAX_VALUE;
                    return Integer.compare(sa, sb);
                })
                .map(ServiceResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ServiceResponse get(@PathVariable Long id) {
        return ServiceResponse.from(serviceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Service")));
    }

    private ServiceCategory resolveCategory(Long categoryId, String name) {
        if (categoryId != null) {
            return categoryRepository.findById(categoryId)
                    .orElseThrow(() -> ApiException.notFound("Service category"));
        }
        if (name != null && !name.isBlank()) {
            return categoryRepository.findByName(name.trim()).orElseGet(() -> {
                ServiceCategory c = new ServiceCategory();
                c.setName(name.trim());
                return categoryRepository.save(c);
            });
        }
        return categoryRepository.findAll().stream()
                .min((a, b) -> Integer.compare(a.getSortOrder(), b.getSortOrder()))
                .orElseGet(() -> {
                    ServiceCategory c = new ServiceCategory();
                    c.setName("General");
                    return categoryRepository.save(c);
                });
    }

    @PostMapping
    public ServiceResponse create(@Valid @RequestBody ServiceRequest req) {
        AuthUser actor = requireActor();
        Service s = new Service();
        s.setName(req.name().trim());
        s.setDescription(req.description());
        s.setCategory(resolveCategory(req.categoryId(), req.category()));
        s.setIsActive(req.isActive() == null || req.isActive());
        s.setIcon(req.icon());
        s.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        s.setCreatedAt(LocalDateTime.now());
        audit(actor, "SERVICE_CREATE", s.getId());
        return ServiceResponse.from(serviceRepository.save(s));
    }

    @PutMapping("/{id}")
    public ServiceResponse update(@PathVariable Long id, @Valid @RequestBody ServiceRequest req) {
        AuthUser actor = requireActor();
        Service s = serviceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Service"));
        s.setName(req.name().trim());
        s.setDescription(req.description());
        s.setCategory(resolveCategory(req.categoryId(), req.category()));
        s.setIsActive(req.isActive() == null || req.isActive());
        s.setIcon(req.icon());
        s.setSortOrder(req.sortOrder() == null ? 0 : req.sortOrder());
        audit(actor, "SERVICE_UPDATE", s.getId());
        return ServiceResponse.from(serviceRepository.save(s));
    }

    @PostMapping("/{id}/deactivate")
    public ServiceResponse deactivate(@PathVariable Long id) {
        AuthUser actor = requireActor();
        Service s = serviceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Service"));
        s.setIsActive(false);
        s.setDeactivatedAt(LocalDateTime.now());
        audit(actor, "SERVICE_DEACTIVATE", s.getId());
        return ServiceResponse.from(serviceRepository.save(s));
    }

    @PostMapping("/{id}/activate")
    public ServiceResponse activate(@PathVariable Long id) {
        AuthUser actor = requireActor();
        Service s = serviceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Service"));
        s.setIsActive(true);
        s.setDeactivatedAt(null);
        audit(actor, "SERVICE_ACTIVATE", s.getId());
        return ServiceResponse.from(serviceRepository.save(s));
    }

    @DeleteMapping("/{id}/hard")
    @org.springframework.transaction.annotation.Transactional
    public ResponseEntity<Void> hardDelete(@PathVariable Long id,
                                           @Valid @RequestBody HardDeleteUserRequest req) {
        AuthUser actor = requireActor();
        Service service = serviceRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Service"));

        boolean eligible = service.getDeactivatedAt() != null
                && service.getDeactivatedAt().plusDays(7).isBefore(LocalDateTime.now());
        if (!eligible) {
            // Immediate delete (active service, or deactivated < 7 days) requires the acting admin's own password
            User actorRow = userRepository.findById(actor.id())
                    .orElseThrow(() -> ApiException.notFound("User"));
            if (!passwordEncoder.matches(req.password(), actorRow.getPasswordHash())) {
                throw ApiException.forbidden("Password confirmation failed");
            }
        }

        serviceRepository.delete(service);
        serviceRepository.flush();
        audit(actor, "SERVICE_HARD_DELETE", id);
        return ResponseEntity.noContent().build();
    }
}
