package com.secphils.controller;

import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
import com.secphils.dto.ProjectHardDeleteRequest;
import com.secphils.dto.ProjectRequest;
import com.secphils.dto.ProjectResponse;
import com.secphils.entity.Company;
import com.secphils.entity.Project;
import com.secphils.entity.Service;
import com.secphils.repository.CompanyRepository;
import com.secphils.repository.ProjectRepository;
import com.secphils.repository.ServiceRepository;
import com.secphils.security.AuthUser;
import com.secphils.security.CurrentUser;
import com.secphils.service.ProjectArchiveService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectRepository projectRepository;
    private final CompanyRepository companyRepository;
    private final ServiceRepository serviceRepository;
    private final AuditService auditService;
    private final ProjectArchiveService archiveService;

    public ProjectController(ProjectRepository projectRepository, CompanyRepository companyRepository,
                             ServiceRepository serviceRepository, AuditService auditService,
                             ProjectArchiveService archiveService) {
        this.projectRepository = projectRepository;
        this.companyRepository = companyRepository;
        this.serviceRepository = serviceRepository;
        this.auditService = auditService;
        this.archiveService = archiveService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<Page<ProjectResponse>> list(
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        AuthUser actor = CurrentUser.require();
        // Staff and clients are locked to their own company; admin sees all
        // (may narrow with ?companyId=) — mirrors DocumentController's role model.
        Long effectiveCompanyId = companyId;
        if (!actor.isAdmin()) {
            if (companyId != null && !companyId.equals(actor.getCompanyId())) {
                throw ApiException.forbidden("You can only view projects of your own company");
            }
            if (actor.getCompanyId() == null) {
                return ResponseEntity.ok(Page.empty(pageable));
            }
            effectiveCompanyId = actor.getCompanyId();
        }
        final Long scopeCompanyId = effectiveCompanyId;
        Pageable page = pageable.getSort().isUnsorted()
                ? org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "createdAt"))
                : pageable;
        Specification<Project> spec = (root, query, cb) -> {
            if (scopeCompanyId == null && status == null && (search == null || search.isBlank())) {
                return cb.conjunction();
            }
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (scopeCompanyId != null) predicates.add(cb.equal(root.get("company").get("id"), scopeCompanyId));
            if (status != null && !status.isBlank()) predicates.add(cb.equal(root.get("status"), status));
            if (search != null && !search.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + search.toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return ResponseEntity.ok(projectRepository.findAll(spec, page).map(ProjectResponse::from));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody ProjectRequest req,
                                                  HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        if (!actor.isAdmin() && (req.companyId() == null || !req.companyId().equals(actor.getCompanyId()))) {
            throw ApiException.forbidden("You can only create projects for your own company");
        }
        Project project = new Project();
        apply(project, req);
        project = projectRepository.save(project);
        auditService.audit(actor, "PROJECT_CREATE", "Project", project.getId(), "Name: " + project.getName(), http);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProjectResponse.from(project));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ProjectResponse> get(@PathVariable Long id) {
        AuthUser actor = CurrentUser.require();
        Project project = projectRepository.findById(id).orElseThrow(() -> ApiException.notFound("Project"));
        requireVisibleTo(actor, project.getCompany().getId());
        return ResponseEntity.ok(ProjectResponse.from(project));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<ProjectResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody ProjectRequest req,
                                                  HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        Project project = projectRepository.findById(id).orElseThrow(() -> ApiException.notFound("Project"));
        requireVisibleTo(actor, project.getCompany().getId());
        if (!actor.isAdmin() && (req.companyId() == null || !req.companyId().equals(actor.getCompanyId()))) {
            throw ApiException.forbidden("You can only update projects of your own company");
        }
        apply(project, req);
        project = projectRepository.save(project);
        auditService.audit(actor, "PROJECT_UPDATE", "Project", project.getId(), "Name: " + project.getName(), http);
        return ResponseEntity.ok(ProjectResponse.from(project));
    }

    /**
     * Archive (soft delete). Staff only — clients can never archive.
     * Stamps archived_at/delete_at, relocates S3 objects under a timestamped
     * archive prefix, and notifies the project's company members.
     */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> archive(@PathVariable Long id, HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        archiveService.archive(actor, id);
        auditService.audit(actor, "PROJECT_ARCHIVE", "Project", id,
                "Name: " + projectRepository.findById(id).map(Project::getName).orElse("?"), http);
        return ResponseEntity.noContent().build();
    }

    /** Restore an archived project (undo a soft delete). Staff only. */
    @PostMapping("/{id}/restore")
    @Transactional
    public ResponseEntity<ProjectResponse> restore(@PathVariable Long id, HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        Project project = archiveService.restore(actor, id);
        auditService.audit(actor, "PROJECT_RESTORE", "Project", id,
                "Name: " + project.getName(), http);
        return ResponseEntity.ok(ProjectResponse.from(project));
    }

    /**
     * Permanently delete an archived project (DB rows + S3 objects).
     * Admins only; requires the account password when the 7-day window
     * hasn't elapsed yet.
     */
    @DeleteMapping("/{id}/hard")
    @Transactional
    public ResponseEntity<Void> hardDelete(@PathVariable Long id,
                                           @RequestBody(required = false) ProjectHardDeleteRequest req,
                                           HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        archiveService.hardDelete(actor, id, req == null ? null : req.password);
        auditService.audit(actor, "PROJECT_HARD_DELETE", "Project", id,
                "Force: " + (req != null && req.password != null && !req.password.isBlank()), http);
        return ResponseEntity.noContent().build();
    }

    private void apply(Project project, ProjectRequest req) {
        Company company = companyRepository.findById(req.companyId())
                .orElseThrow(() -> ApiException.notFound("Company"));
        project.setCompany(company);
        if (req.serviceId() != null) {
            Service service = serviceRepository.findById(req.serviceId())
                    .orElseThrow(() -> ApiException.notFound("Service"));
            project.setService(service);
        }
        project.setName(req.name());
        project.setScope(req.scope());
        project.setObjectives(req.objectives());
        project.setDeliverables(req.deliverables());
        if (req.status() != null && !req.status().isBlank()) project.setStatus(req.status());
        project.setTotalCost(req.totalCost());
        project.setRawMaterials(req.rawMaterials());
        project.setProductionOutput(req.productionOutput());
        project.setWasteManagement(req.wasteManagement());
        project.setWasteMaterials(req.wasteMaterials());
        project.setManufacturingProcedure(req.manufacturingProcedure());
        project.setProductionFlowchartUrl(req.productionFlowchartUrl());
        project.setDueDate(req.dueDate());
        project.setProgress(req.progress() != null ? req.progress() : 0);
    }

    /** Clients/staff may only touch projects of their own company; admin is unrestricted. */
    private void requireVisibleTo(AuthUser actor, Long companyId) {
        if (!actor.isAdmin() && !companyId.equals(actor.getCompanyId())) {
            throw ApiException.notFound("Project"); // 404, not 403 — don't reveal other companies' data
        }
    }
}
