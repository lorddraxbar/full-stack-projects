package com.secphils.controller;

import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
import com.secphils.dto.AnnouncementRequest;
import com.secphils.dto.AnnouncementResponse;
import com.secphils.entity.Announcement;
import com.secphils.entity.Company;
import com.secphils.entity.Project;
import com.secphils.repository.AnnouncementRepository;
import com.secphils.repository.CompanyRepository;
import com.secphils.repository.ProjectRepository;
import com.secphils.security.AuthUser;
import com.secphils.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import com.secphils.entity.User;
import com.secphils.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/announcements")
public class AnnouncementController {

    private final AnnouncementRepository announcementRepository;
    private final CompanyRepository companyRepository;
    private final ProjectRepository projectRepository;
    private final AuditService auditService;
    private final UserRepository userRepository;

    public AnnouncementController(AnnouncementRepository announcementRepository,
                                  CompanyRepository companyRepository,
                                  ProjectRepository projectRepository, UserRepository userRepository,
                              AuditService auditService) {
        this.announcementRepository = announcementRepository;
        this.companyRepository = companyRepository;
        this.projectRepository = projectRepository;
        this.auditService = auditService;
        this.userRepository = userRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<AnnouncementResponse>> list(
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String audience) {
        List<Announcement> items;
        if (companyId != null) {
            items = announcementRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);
        } else if (audience != null && !audience.isBlank()) {
            items = announcementRepository.findByAudienceOrderByCreatedAtDesc(audience);
        } else {
            items = announcementRepository.findAll();
        }
        return ResponseEntity.ok(items.stream().map(AnnouncementResponse::from).toList());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<AnnouncementResponse> create(@Valid @RequestBody AnnouncementRequest req,
                                                       HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        Announcement a = new Announcement();
        apply(a, req);
        a.setCreatedBy(userRepository.findById(actor.id())
                .orElseThrow(() -> ApiException.notFound("User")));
        a.setCreatedAt(LocalDateTime.now());
        a = announcementRepository.save(a);
        auditService.audit(actor, "ANNOUNCEMENT_CREATE", "Announcement", a.getId(),
                "Title: " + a.getTitle(), http);
        return ResponseEntity.status(HttpStatus.CREATED).body(AnnouncementResponse.from(a));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<AnnouncementResponse> get(@PathVariable Long id) {
        Announcement a = announcementRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Announcement"));
        return ResponseEntity.ok(AnnouncementResponse.from(a));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<AnnouncementResponse> update(@PathVariable Long id,
                                                       @Valid @RequestBody AnnouncementRequest req,
                                                       HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        Announcement a = announcementRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Announcement"));
        apply(a, req);
        a = announcementRepository.save(a);
        auditService.audit(actor, "ANNOUNCEMENT_UPDATE", "Announcement", a.getId(),
                "Title: " + a.getTitle(), http);
        return ResponseEntity.ok(AnnouncementResponse.from(a));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        Announcement a = announcementRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Announcement"));
        announcementRepository.delete(a);
        auditService.audit(actor, "ANNOUNCEMENT_DELETE", "Announcement", id,
                "Title: " + a.getTitle(), http);
        return ResponseEntity.noContent().build();
    }

    private void apply(Announcement a, AnnouncementRequest req) {
        if (req.companyId() != null) {
            Company company = companyRepository.findById(req.companyId())
                    .orElseThrow(() -> ApiException.notFound("Company"));
            a.setCompany(company);
        }
        if (req.projectId() != null) {
            Project project = projectRepository.findById(req.projectId())
                    .orElseThrow(() -> ApiException.notFound("Project"));
            a.setProject(project);
        }
        a.setTitle(req.title());
        a.setBody(req.body());
        if (req.category() != null && !req.category().isBlank()) a.setCategory(req.category());
        if (req.audience() != null && !req.audience().isBlank()) a.setAudience(req.audience());
        if (req.isPublished() != null) a.setIsPublished(req.isPublished());
    }
}
