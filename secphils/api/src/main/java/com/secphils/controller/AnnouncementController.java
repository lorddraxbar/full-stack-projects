package com.secphils.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
import com.secphils.dto.AnnouncementRequest;
import com.secphils.dto.AnnouncementResponse;
import com.secphils.entity.Announcement;
import com.secphils.entity.Company;
import com.secphils.entity.Notification;
import com.secphils.entity.NotificationPreference;
import com.secphils.entity.Project;
import com.secphils.entity.User;
import com.secphils.repository.AnnouncementRepository;
import com.secphils.repository.CompanyRepository;
import com.secphils.repository.NotificationPreferenceRepository;
import com.secphils.repository.NotificationRepository;
import com.secphils.repository.ProjectRepository;
import com.secphils.repository.UserRepository;
import com.secphils.security.AuthUser;
import com.secphils.security.CurrentUser;
import com.secphils.service.MailService;
import com.secphils.service.EmailTemplateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

/**
 * Company announcements.
 *
 * <p>Visibility model:
 * <ul>
 *   <li><b>CLIENT</b> — sees only published announcements of their own company.
 *       (The frontend narrows project-scoped ones to projects the client can see.)</li>
 *   <li><b>USER / ADMIN</b> — sees every announcement (published and draft) of
 *       their own company; ADMIN may also pass {@code ?companyId=} for another.</li>
 * </ul>
 *
 * <p>Publishing (create as published, or update to published) fans out an in-app
 * {@link Notification} row plus a branded email to every active member of the
 * company whose notification preferences allow the {@code announcement} key on
 * that channel. Mail failures never break the request (MailService logs only).
 */
@RestController
@RequestMapping("/api/v1/announcements")
public class AnnouncementController {

    private static final String PREF_KEY = "announcement";

    private final AnnouncementRepository announcementRepository;
    private final CompanyRepository companyRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationRepository notificationRepository;
    private final AuditService auditService;
    private final MailService mailService;
    private final EmailTemplateService templateService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String portalBaseUrl;

    public AnnouncementController(AnnouncementRepository announcementRepository,
                                  CompanyRepository companyRepository,
                                  ProjectRepository projectRepository,
                                  UserRepository userRepository,
                                  NotificationPreferenceRepository preferenceRepository,
                                  NotificationRepository notificationRepository,
                                  AuditService auditService,
                                  MailService mailService,
                                  EmailTemplateService templateService,
                                  @Value("${app.invite.base-url}") String portalBaseUrl) {
        this.announcementRepository = announcementRepository;
        this.companyRepository = companyRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.preferenceRepository = preferenceRepository;
        this.notificationRepository = notificationRepository;
        this.auditService = auditService;
        this.mailService = mailService;
        this.templateService = templateService;
        this.portalBaseUrl = portalBaseUrl;
    }

    // ---------- helpers ----------

    private static void requireStaff() {
        AuthUser actor = CurrentUser.require();
        if (!actor.isUserOrAdmin()) {
            throw ApiException.forbidden("Only staff can manage announcements");
        }
    }

    /**
     * Resolves the company an announcement belongs to.
     * Explicit {@code companyId} is honored for staff/admin (clients are locked
     * to their own); defaults to the actor's own company.
     */
    private Company resolveCompany(AuthUser actor, Long requestedCompanyId) {
        Long target;
        if (requestedCompanyId != null) {
            if (actor.isClient()) {
                throw ApiException.forbidden("Clients can only post announcements for their own company");
            }
            target = requestedCompanyId;
        } else {
            target = actor.getCompanyId();
            if (target == null) {
                throw ApiException.forbidden("Your account is not associated with a company");
            }
        }
        Company company = companyRepository.findById(target)
                .orElseThrow(() -> ApiException.notFound("Company"));
        if (!actor.isAdmin() && !target.equals(actor.getCompanyId())) {
            throw ApiException.forbidden("You can only manage announcements for your own company");
        }
        return company;
    }

    /** Loads the target announcement, treating out-of-company rows as 404 (admins: any company). */
    private Announcement loadInScope(AuthUser actor, Long id) {
        Announcement a = announcementRepository.findById(id)
                .filter(x -> x.getCompany() != null)
                .orElseThrow(() -> ApiException.notFound("Announcement"));
        if (!actor.isAdmin()) {
            Long mine = actor.getCompanyId();
            if (mine == null || !mine.equals(a.getCompany().getId())) {
                throw ApiException.notFound("Announcement");
            }
        }
        return a;
    }

    private Project resolveProject(Company company, Long projectId) {
        if (projectId == null) return null;
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> ApiException.notFound("Project"));
        if (p.getCompany() == null || !p.getCompany().getId().equals(company.getId())) {
            throw ApiException.badRequest("Selected project does not belong to this company");
        }
        return p;
    }

    /** Applies request fields onto the entity (company/project pre-resolved by callers). */
    private void applyFields(Announcement a, AnnouncementRequest req, Company company, Project project) {
        a.setCompany(company);
        a.setProject(project);
        a.setTitle(req.title());
        a.setBody(req.body());
        if (req.category() != null && !req.category().isBlank()) a.setCategory(req.category());
        if (req.audience() != null && !req.audience().isBlank()) a.setAudience(req.audience());
        if (req.isPublished() != null) a.setIsPublished(req.isPublished());
    }

    /** Reads a stored preference flag; missing rows / bad JSON fall back to default-ON. */
    private boolean prefAllows(User recipient, String channelJson) {
        try {
            if (channelJson == null || channelJson.isBlank()) return true;
            Map<?, ?> m = objectMapper.readValue(channelJson, Map.class);
            Object v = m.get(PREF_KEY);
            return v == null || Boolean.TRUE.equals(v);
        } catch (Exception e) {
            return true;
        }
    }

    /** Fans the announcement out to the company's active members (skipping the author). */
    private void dispatch(Announcement a, AuthUser actor, HttpServletRequest request) {
        String projectRef = a.getProject() != null ? " · " + a.getProject().getName() : "";
        String title = "New announcement: " + a.getTitle() + projectRef;
        String body = a.getBody() == null ? "" : a.getBody();
        String link = portalBaseUrl.endsWith("/") ? portalBaseUrl + "announcements" : portalBaseUrl + "/announcements";

        for (User u : userRepository.findByCompanyIdAndIsActiveTrue(a.getCompany().getId())) {
            if (u.getId().equals(actor.id())) continue; // author already knows
            NotificationPreference pref = preferenceRepository.findByUserId(u.getId()).orElse(null);
            boolean inApp = prefAllows(u, pref == null ? null : pref.getInApp());
            boolean email = prefAllows(u, pref == null ? null : pref.getEmail());

            if (inApp) {
                Notification n = new Notification();
                User ref = new User();
                ref.setId(u.getId());
                n.setRecipient(ref);
                n.setTitle(title);
                n.setBody(body);
                n.setType("ANNOUNCEMENT");
                n.setEntityType("Announcement");
                n.setEntityId(a.getId());
                n.setIsRead(false);
                n.setCreatedAt(LocalDateTime.now());
                notificationRepository.save(n);
            }
            if (email && u.getEmail() != null && !u.getEmail().isBlank()) {
                Map<String, String> vars = Map.of(
                        "title", a.getTitle() == null ? "" : a.getTitle(),
                        "category", a.getCategory() == null ? "Update" : a.getCategory().replace('_', ' '),
                        "projectRef", a.getProject() != null && a.getProject().getName() != null
                                ? " — " + a.getProject().getName() : "",
                        "body", body,
                        "company", a.getCompany() != null ? a.getCompany().getName() : "your company");
                mailService.sendHtml(u.getEmail(),
                        templateService.subject(EmailTemplateService.ANNOUNCEMENT, vars),
                        announcementEmail(EmailTemplateService.ANNOUNCEMENT, vars, link),
                        link);
            }
        }
        auditService.audit(actor, "ANNOUNCEMENT_PUBLISH", "Announcement", a.getId(),
                "Title: " + a.getTitle(), request);
    }

    /** Card built from the admin-editable announcement template. */
    private String announcementEmail(String templateName, Map<String, String> vars, String link) {
        return templateService.brandedCard(
                templateService.kicker(templateName, vars),
                templateService.heading(templateName, vars),
                templateService.bodyHtml(templateName, vars),
                templateService.cta(templateName, vars),
                link,
                templateService.footer(templateName, vars));
    }

    // ---------- endpoints ----------

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<AnnouncementResponse>> list(
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String audience) {
        AuthUser actor = CurrentUser.require();
        Long effective;
        if (companyId != null) {
            if (actor.isClient()) {
                throw ApiException.forbidden("Clients can only view announcements of their own company");
            }
            if (!actor.isAdmin() && !companyId.equals(actor.getCompanyId())) {
                throw ApiException.forbidden("You can only view announcements of your own company");
            }
            effective = companyId;
        } else {
            effective = actor.getCompanyId();
            if (effective == null) {
                throw ApiException.forbidden("Your account is not associated with a company");
            }
        }
        List<Announcement> items = new ArrayList<>(announcementRepository.findByCompanyIdOrderByCreatedAtDesc(effective));
        if (audience != null && !audience.isBlank()) {
            items.removeIf(x -> !audience.equals(x.getAudience()));
        }
        if (actor.isClient()) {
            items.removeIf(x -> !Boolean.TRUE.equals(x.getIsPublished())); // clients never see drafts
        }
        items.sort((x, y) -> {
            int px = Boolean.TRUE.equals(x.getIsPublished()) ? 0 : 1;
            int py = Boolean.TRUE.equals(y.getIsPublished()) ? 0 : 1;
            if (px != py) return px - py;
            return y.getCreatedAt() == null ? -1 : (x.getCreatedAt() == null ? 1 : y.getCreatedAt().compareTo(x.getCreatedAt()));
        });
        return ResponseEntity.ok(items.stream().map(AnnouncementResponse::from).toList());
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<AnnouncementResponse> get(@PathVariable Long id) {
        AuthUser actor = CurrentUser.require();
        Announcement a = loadInScope(actor, id);
        // Same rule as the list endpoint: clients may only see published rows,
        // even when they arrive directly at a draft's URL.
        if (actor.isClient() && !Boolean.TRUE.equals(a.getIsPublished())) {
            throw ApiException.notFound("Announcement");
        }
        return ResponseEntity.ok(AnnouncementResponse.from(a));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<AnnouncementResponse> create(@Valid @RequestBody AnnouncementRequest req,
                                                       HttpServletRequest request) {
        requireStaff();
        AuthUser actor = CurrentUser.require();
        Company company = resolveCompany(actor, req.companyId());
        Project project = resolveProject(company, req.projectId());

        Announcement a = new Announcement();
        applyFields(a, req, company, project);
        a.setCreatedBy(userRepository.findById(actor.id())
                .orElseThrow(() -> ApiException.notFound("User")));
        a.setCreatedAt(LocalDateTime.now());
        a = announcementRepository.save(a);

        if (Boolean.TRUE.equals(a.getIsPublished())) {
            dispatch(a, actor, request);
        }
        auditService.audit(actor, "ANNOUNCEMENT_CREATE", "Announcement", a.getId(),
                "Title: " + a.getTitle(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(AnnouncementResponse.from(a));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<AnnouncementResponse> update(@PathVariable Long id,
                                                       @Valid @RequestBody AnnouncementRequest req,
                                                       HttpServletRequest request) {
        requireStaff();
        AuthUser actor = CurrentUser.require();
        Announcement a = loadInScope(actor, id);
        Company company = resolveCompany(actor, req.companyId() != null ? req.companyId() : a.getCompany().getId());
        Project project = resolveProject(company, req.projectId());

        boolean wasPublished = Boolean.TRUE.equals(a.getIsPublished());
        applyFields(a, req, company, project);
        a = announcementRepository.save(a);

        if (Boolean.TRUE.equals(a.getIsPublished()) && !wasPublished) {
            dispatch(a, actor, request); // re-publishing re-announces to the company
        }
        auditService.audit(actor, "ANNOUNCEMENT_UPDATE", "Announcement", a.getId(),
                "Title: " + a.getTitle() + " / published: " + a.getIsPublished(), request);
        return ResponseEntity.ok(AnnouncementResponse.from(a));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        requireStaff();
        AuthUser actor = CurrentUser.require();
        Announcement a = loadInScope(actor, id);
        if (!actor.isAdmin() && (a.getCreatedBy() == null || !actor.id().equals(a.getCreatedBy().getId()))) {
            throw ApiException.forbidden("Only the author or an admin can delete this announcement");
        }
        announcementRepository.delete(a);
        auditService.audit(actor, "ANNOUNCEMENT_DELETE", "Announcement", id,
                "Title: " + a.getTitle(), request);
        return ResponseEntity.noContent().build();
    }
}
