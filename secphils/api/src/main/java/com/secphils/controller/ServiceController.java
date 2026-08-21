package com.secphils.controller;

import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
import com.secphils.dto.ServiceRequest;
import com.secphils.dto.ServiceResponse;
import com.secphils.entity.Service;
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
@RequestMapping("/api/v1/services")
public class ServiceController {

    private final ServiceRepository serviceRepository;
    private final AuditService auditService;

    public ServiceController(ServiceRepository serviceRepository, AuditService auditService) {
        this.serviceRepository = serviceRepository;
        this.auditService = auditService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<ServiceResponse>> list(@RequestParam(required = false) String category) {
        List<Service> items = serviceRepository.findAll().stream()
                .filter(s -> category == null || category.isBlank() || category.equalsIgnoreCase(s.getCategory()))
                .sorted(java.util.Comparator.comparing(Service::getSortOrder, java.util.Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(Service::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
        return ResponseEntity.ok(items.stream().map(ServiceResponse::from).toList());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ServiceResponse> create(@Valid @RequestBody ServiceRequest req,
                                                  HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        Service service = new Service();
        apply(service, req);
        service.setCreatedAt(LocalDateTime.now());
        service = serviceRepository.save(service);
        auditService.audit(actor, "SERVICE_CREATE", "Service", service.getId(), "Name: " + service.getName(), http);
        return ResponseEntity.status(HttpStatus.CREATED).body(ServiceResponse.from(service));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ServiceResponse> get(@PathVariable Long id) {
        Service service = serviceRepository.findById(id).orElseThrow(() -> ApiException.notFound("Service"));
        return ResponseEntity.ok(ServiceResponse.from(service));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<ServiceResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody ServiceRequest req,
                                                  HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        Service service = serviceRepository.findById(id).orElseThrow(() -> ApiException.notFound("Service"));
        apply(service, req);
        service = serviceRepository.save(service);
        auditService.audit(actor, "SERVICE_UPDATE", "Service", service.getId(), "Name: " + service.getName(), http);
        return ResponseEntity.ok(ServiceResponse.from(service));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deactivate(@PathVariable Long id, HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        Service service = serviceRepository.findById(id).orElseThrow(() -> ApiException.notFound("Service"));
        service.setIsActive(false);
        serviceRepository.save(service);
        auditService.audit(actor, "SERVICE_DEACTIVATE", "Service", service.getId(), "Name: " + service.getName(), http);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/activate")
    @Transactional
    public ResponseEntity<ServiceResponse> activate(@PathVariable Long id, HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        Service service = serviceRepository.findById(id).orElseThrow(() -> ApiException.notFound("Service"));
        service.setIsActive(true);
        serviceRepository.save(service);
        auditService.audit(actor, "SERVICE_ACTIVATE", "Service", service.getId(), "Name: " + service.getName(), http);
        return ResponseEntity.ok(ServiceResponse.from(service));
    }

    private void apply(Service service, ServiceRequest req) {
        service.setName(req.name());
        service.setDescription(req.description());
        if (req.category() != null && !req.category().isBlank()) service.setCategory(req.category());
        if (req.icon() != null && !req.icon().isBlank()) service.setIcon(req.icon());
        if (req.sortOrder() != null) service.setSortOrder(req.sortOrder());
        if (req.isActive() != null) service.setIsActive(req.isActive());
    }
}
