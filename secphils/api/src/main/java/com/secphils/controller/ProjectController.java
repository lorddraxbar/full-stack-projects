package com.secphils.controller;

import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
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

    public ProjectController(ProjectRepository projectRepository, CompanyRepository companyRepository,
                             ServiceRepository serviceRepository, AuditService auditService) {
        this.projectRepository = projectRepository;
        this.companyRepository = companyRepository;
        this.serviceRepository = serviceRepository;
        this.auditService = auditService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<Page<ProjectResponse>> list(
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        Pageable page = pageable.getSort().isUnsorted()
                ? org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "createdAt"))
                : pageable;
        Specification<Project> spec = (root, query, cb) -> {
            if (companyId == null && status == null && (search == null || search.isBlank())) {
                return cb.conjunction();
            }
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (companyId != null) predicates.add(cb.equal(root.get("company").get("id"), companyId));
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
        Project project = new Project();
        apply(project, req);
        project = projectRepository.save(project);
        auditService.audit(actor, "PROJECT_CREATE", "Project", project.getId(), "Name: " + project.getName(), http);
        return ResponseEntity.status(HttpStatus.CREATED).body(ProjectResponse.from(project));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ProjectResponse> get(@PathVariable Long id) {
        Project project = projectRepository.findById(id).orElseThrow(() -> ApiException.notFound("Project"));
        return ResponseEntity.ok(ProjectResponse.from(project));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<ProjectResponse> update(@PathVariable Long id,
                                                  @Valid @RequestBody ProjectRequest req,
                                                  HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        Project project = projectRepository.findById(id).orElseThrow(() -> ApiException.notFound("Project"));
        apply(project, req);
        project = projectRepository.save(project);
        auditService.audit(actor, "PROJECT_UPDATE", "Project", project.getId(), "Name: " + project.getName(), http);
        return ResponseEntity.ok(ProjectResponse.from(project));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> archive(@PathVariable Long id, HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        Project project = projectRepository.findById(id).orElseThrow(() -> ApiException.notFound("Project"));
        project.setStatus("ARCHIVED");
        projectRepository.save(project);
        auditService.audit(actor, "PROJECT_ARCHIVE", "Project", project.getId(), "Name: " + project.getName(), http);
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
    }
}
