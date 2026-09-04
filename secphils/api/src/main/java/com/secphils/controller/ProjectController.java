package com.secphils.controller;

import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
import com.secphils.dto.ProjectHardDeleteRequest;
import com.secphils.dto.ProjectRequest;
import com.secphils.dto.ProjectResponse;
import com.secphils.entity.Company;
import com.secphils.entity.Message;
import com.secphils.entity.Project;
import com.secphils.entity.Service;
import com.secphils.entity.User;
import com.secphils.repository.CompanyRepository;
import com.secphils.repository.MessageRepository;
import com.secphils.repository.ProjectRepository;
import com.secphils.repository.ServiceRepository;
import com.secphils.security.AuthUser;
import com.secphils.security.CurrentUser;
import com.secphils.policy.DisplayNamePolicy;
import com.secphils.service.ProjectArchiveService;
import com.secphils.service.ProjectNotificationService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
    private final MessageRepository messageRepository;
    private final AuditService auditService;
    private final ProjectArchiveService archiveService;
    private final ProjectNotificationService notificationService;

    public ProjectController(ProjectRepository projectRepository, CompanyRepository companyRepository,
                             ServiceRepository serviceRepository, MessageRepository messageRepository,
                             AuditService auditService,
                             ProjectArchiveService archiveService,
                             ProjectNotificationService notificationService) {
        this.projectRepository = projectRepository;
        this.companyRepository = companyRepository;
        this.serviceRepository = serviceRepository;
        this.messageRepository = messageRepository;
        this.auditService = auditService;
        this.archiveService = archiveService;
        this.notificationService = notificationService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<Page<ProjectResponse>> list(
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        AuthUser actor = CurrentUser.require();
        // Clients are locked to their own company; admin AND staff (USER) see all
        // (may narrow with ?companyId=) — the staff dashboard mirrors the admin
        // one, and staff already had cross-company read on documents/messages/team.
        // Write paths (create/update) keep their own stricter owner checks.
        Long effectiveCompanyId = companyId;
        if (actor.isClient()) {
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
        Page<Project> projects = projectRepository.findAll(spec, page);
        // Latest message per project (for the "latest update" column) — one
        // query scoped to the page's project ids. CLIENT viewers use the
        // internal-excluding variant so a staff-only message can never leak
        // into a client-facing preview; staff/admin see the true latest.
        List<Long> pageIds = projects.getContent().stream().map(Project::getId).toList();
        Map<Long, Message> latestByProject = pageIds.isEmpty()
                ? Map.of()
                : (actor.isClient()
                        ? messageRepository.findLatestNonInternalPerProject(pageIds)
                        : messageRepository.findLatestPerProject(pageIds)).stream()
                        .collect(Collectors.toMap(
                                m -> m.getProject().getId(), m -> m, (a, b) -> a));
        // Per-project message count for the Messages inbox badge — one query,
        // internal-excluding for clients so a staff-only thread never inflates a
        // client's count (same privacy rule as the latest-message preview).
        Map<Long, Long> countByProject = pageIds.isEmpty()
                ? Map.of()
                : (actor.isClient()
                        ? messageRepository.countNonInternalPerProject(pageIds)
                        : messageRepository.countPerProject(pageIds)).stream()
                        .collect(Collectors.toMap(
                                row -> toLong(row.get("projectId")),
                                row -> toLong(row.get("cnt")), (a, b) -> a));
        Page<ProjectResponse> mapped = projects.map(p -> {
            Message latest = latestByProject.get(p.getId());
            // Sender name mirrors MessageController: internal messages surface the
            // real colleague's name (they never reach a client); everything else
            // stays brand-masked for provider senders.
            String sender = null;
            String visibility = null;
            boolean hasFile = false;
            if (latest != null) {
                visibility = latest.getVisibility() == null ? "CLIENT" : latest.getVisibility();
                hasFile = latest.getAttachmentFileName() != null && !latest.getAttachmentFileName().isBlank();
                if ("INTERNAL".equalsIgnoreCase(visibility) && latest.getSender() != null) {
                    String real = latest.getSender().getFullName();
                    if (real == null || real.isBlank()) real = latest.getSender().getEmail();
                    sender = (real == null || real.isBlank()) ? null : real;
                } else {
                    sender = DisplayNamePolicy.nameFor(latest.getSender());
                }
            }
            Long cnt = countByProject.get(p.getId());
            return ProjectResponse.from(p,
                    latest != null ? latest.getBody() : null,
                    latest != null ? latest.getCreatedAt() : null,
                    sender,
                    visibility,
                    latest != null ? hasFile : null,
                    cnt != null ? cnt.intValue() : 0);
        });
        return ResponseEntity.ok(mapped);
    }

    private static long toLong(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        return Long.parseLong(o.toString());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody ProjectRequest req,
                                                  HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        if (req.serviceId() == null) {
            throw ApiException.badRequest("Service type is required");
        }
        if (!actor.isAdmin() && (req.companyId() == null || !req.companyId().equals(actor.getCompanyId()))) {
            throw ApiException.forbidden("You can only create projects for your own company");
        }
        Project project = new Project();
        apply(project, req);
        if (req.status() == null || req.status().isBlank()) {
            // Entity default is NOT_STARTED, but a submitted project is actively
            // waiting for the authorized rep's review/completion — "In Progress".
            project.setStatus("IN_PROGRESS");
        }
        project = projectRepository.save(project);
        auditService.audit(actor, "PROJECT_CREATE", "Project", project.getId(), "Name: " + project.getName(), http);
        notificationService.onProjectCreated(project, actor.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(ProjectResponse.from(project));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<ProjectResponse> get(@PathVariable Long id) {
        AuthUser actor = CurrentUser.require();
        Project project = projectRepository.findById(id).orElseThrow(() -> ApiException.notFound("Project"));
        requireReadableBy(actor, project.getCompany().getId());
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
        String oldStatus = project.getStatus();
        apply(project, req);
        if ("COMPLETED".equals(project.getStatus()) && !"COMPLETED".equals(oldStatus)
                && project.getCompletedAt() == null) {
            // Remember the first completion for the list page; a later
            // re-completion refreshes the timestamp.
            project.setCompletedAt(java.time.LocalDateTime.now());
        }
        project = projectRepository.save(project);
        auditService.audit(actor, "PROJECT_UPDATE", "Project", project.getId(), "Name: " + project.getName(), http);
        if (!java.util.Objects.equals(oldStatus, project.getStatus())) {
            notificationService.onStatusChanged(project, oldStatus, project.getStatus(), actor.id());
        }
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
        project.setNotes(req.notes());
        project.setObjectives(req.objectives());
        project.setDeliverables(req.deliverables());
        project.setAddress(req.address());
        if (req.status() != null && !req.status().isBlank()) project.setStatus(req.status());
        project.setTotalCost(req.totalCost());
        project.setRawMaterials(req.rawMaterials());
        project.setProductionOutput(req.productionOutput());
        project.setWasteManagement(req.wasteManagement());
        project.setWasteMaterials(req.wasteMaterials());
        project.setManufacturingProcedure(req.manufacturingProcedure());
        project.setProductionFlowchartUrl(req.productionFlowchartUrl());
        project.setProgress(req.progress() != null ? req.progress() : 0);
    }

    /** Clients/staff may only touch projects of their own company; admin is
     *  unrestricted. The customer company's authorized representative is an
     *  exception: they must be able to open the project (review link) and
     *  mark it complete — that's the whole point of the submission email. */
    private void requireVisibleTo(AuthUser actor, Long companyId) {
        if (actor.isAdmin()) return;
        if (companyId.equals(actor.getCompanyId())) return;
        Company company = companyRepository.findById(companyId).orElse(null);
        User rep = company == null ? null : company.getAuthorizedRep();
        if (rep != null && rep.getId().equals(actor.id())) return;
        throw ApiException.notFound("Project"); // 404, not 403 — don't reveal other companies' data
    }

    /** READ-level gate: admin + staff (USER) can read every project (the staff
     *  dashboard mirrors the admin one); clients stay locked to their own
     *  company (plus their company's authorized rep, same as writes). */
    private void requireReadableBy(AuthUser actor, Long companyId) {
        if (actor.isAdmin() || actor.isUserOrAdmin()) {
            return; // staff & admin: cross-company reads
        }
        if (companyId.equals(actor.getCompanyId())) return;
        Company company = companyRepository.findById(companyId).orElse(null);
        User rep = company == null ? null : company.getAuthorizedRep();
        if (rep != null && rep.getId().equals(actor.id())) return;
        throw ApiException.notFound("Project"); // 404, not 403 — don't reveal other companies' data
    }
}
