package com.secphils.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secphils.entity.Company;
import com.secphils.entity.Notification;
import com.secphils.entity.NotificationPreference;
import com.secphils.entity.Project;
import com.secphils.entity.User;
import com.secphils.repository.NotificationPreferenceRepository;
import com.secphils.repository.NotificationRepository;
import com.secphils.repository.ProjectRepository;
import com.secphils.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Project lifecycle notifications. When a project is created via the wizard
 * or its status changes, the customer's authorized representative and the
 * provider side are notified — an in-app {@link Notification} row plus a
 * branded email for each, honoring each recipient's per-channel
 * "projectStatusChanged" preference (missing preference = allowed, same
 * convention as {@link ProjectArchiveService} and the message fan-out).
 *
 *  onProjectCreated: the project just went live. The authorized rep is asked
 *                   to review and complete it; every other active provider
 *                   user is told the new job exists.
 *
 *  onStatusChanged:  the project moved between statuses (e.g. the rep marked
 *                   it COMPLETED). Rep + provider members hear about it; the
 *                   actor themselves is skipped.
 *
 *  Mail failures are logged, never thrown — a down mailbox must not break
 *  project creation (MailService.sendHtml swallows transport errors; the
 *  in-app row stays the durable record).
 */
@Service
public class ProjectNotificationService {

    private static final Logger log = LoggerFactory.getLogger(ProjectNotificationService.class);
    private static final String PREF_KEY = "projectStatusChanged";

    private final ProjectRepository projects;
    private final UserRepository users;
    private final NotificationRepository notifications;
    private final NotificationPreferenceRepository preferences;
    private final MailService mail;
    private final EmailTemplateService templateService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String portalBaseUrl;

    public ProjectNotificationService(ProjectRepository projects,
                                      UserRepository users,
                                      NotificationRepository notifications,
                                      NotificationPreferenceRepository preferences,
                                      MailService mail,
                                      EmailTemplateService templateService,
                                      @Value("${app.invite.base-url:http://localhost:3000}") String portalBaseUrl) {
        this.projects = projects;
        this.users = users;
        this.notifications = notifications;
        this.preferences = preferences;
        this.mail = mail;
        this.templateService = templateService;
        this.portalBaseUrl = portalBaseUrl;
    }

    @Transactional
    public void onProjectCreated(Project project, Long actorId) {
        String link = projectDetailLink(project.getId());
        String repName = displayName(project.getCompany().getAuthorizedRep());
        // 1) The customer's authorized rep: review + complete the project.
        String repSubject = templateService.subject(EmailTemplateService.PROJECT_CREATED_REP, Map.of(
                "company", companyName(project), "project", projectName(project)));
        deliver(project, project.getCompany().getAuthorizedRep(), actorId,
                "New project submitted — " + project.getName(),
                repSubject,
                templateCard(EmailTemplateService.PROJECT_CREATED_REP, Map.of(
                        "name", firstName(project.getCompany().getAuthorizedRep()),
                        "company", companyName(project),
                        "project", projectName(project)),
                        link),
                link, "NEW_PROJECT");
        // 2) Provider side: every other active staff user.
        String staffSubject = templateService.subject(EmailTemplateService.PROJECT_CREATED_STAFF, Map.of(
                "company", companyName(project), "project", projectName(project)));
        String repNote = repName != null ? ", with " + repName + " as the authorized representative" : "";
        for (User u : activeProviderUsers(project.getCompany())) {
            if (u.getId().equals(actorId)) continue;
            deliver(project, u, actorId,
                    "New project — " + project.getName(),
                    staffSubject,
                    templateCard(EmailTemplateService.PROJECT_CREATED_STAFF, Map.of(
                            "name", firstName(u),
                            "company", companyName(project),
                            "project", projectName(project),
                            "repNote", repNote),
                    link),
                    link, "NEW_PROJECT");
        }
    }

    @Transactional
    public void onStatusChanged(Project project, String oldStatus, String newStatus, Long actorId) {
        if (oldStatus == null || oldStatus.equals(newStatus)) return;
        String label = labelFor(newStatus);
        String link = projectDetailLink(project.getId());
        Map<String, String> vars = Map.of(
                "project", projectName(project),
                "company", companyName(project),
                "statusLabel", label);
        String subject = templateService.subject(EmailTemplateService.PROJECT_STATUS_REP, vars);
        deliver(project, project.getCompany().getAuthorizedRep(), actorId,
                "Project " + label + " — " + project.getName(),
                subject,
                templateCard(EmailTemplateService.PROJECT_STATUS_REP,
                        Map.of("name", firstName(project.getCompany().getAuthorizedRep()),
                                "project", projectName(project),
                                "company", companyName(project),
                                "statusLabel", label),
                        link),
                link, "PROJECT_STATUS");
        for (User u : activeProviderUsers(project.getCompany())) {
            if (u.getId().equals(actorId)) continue;
            deliver(project, u, actorId,
                    "Project " + label + " — " + project.getName(),
                    subject,
                    templateCard(EmailTemplateService.PROJECT_STATUS_STAFF,
                            Map.of("name", firstName(u),
                                    "project", projectName(project),
                                    "company", companyName(project),
                                    "statusLabel", label),
                            link),
                    link, "PROJECT_STATUS");
        }
    }

    /**
     * Provider-side recipients: the active staff accounts (ADMIN/USER roles —
     * this deployment has no PROVIDER role) that are NOT members of the
     * project's customer company.
     */
    private List<User> activeProviderUsers(Company customerCompany) {
        List<User> out = new java.util.ArrayList<>();
        for (User u : users.findAll()) {
            if (u.getIsActive() == null || !u.getIsActive()) continue;
            String role = u.getRole() == null ? "" : u.getRole();
            if (!role.equals("ADMIN") && !role.equals("USER")) continue;
            if (customerCompany != null
                    && customerCompany.getId() != null
                    && customerCompany.getId().equals(u.getCompanyId())) continue;
            out.add(u);
        }
        return out;
    }

    /** In-app row + branded email for one recipient, each channel pref-gated. */
    private void deliver(Project project, User recipient, Long skipIfSameId,
                         String notifTitle, String emailSubject, String emailHtml,
                         String link, String notifType) {
        if (recipient == null || recipient.getId().equals(skipIfSameId)) return;
        NotificationPreference pref = preferences.findByUserId(recipient.getId()).orElse(null);
        if (prefAllows(recipient, pref == null ? null : pref.getInApp())) {
            Notification n = new Notification();
            User ref = new User();
            ref.setId(recipient.getId());
            n.setRecipient(ref);
            n.setTitle(notifTitle);
            n.setBody("Project: " + project.getName() + " — " + companyName(project));
            n.setType(notifType);
            n.setEntityType("Project");
            n.setEntityId(project.getId());
            n.setIsRead(false);
            n.setCreatedAt(LocalDateTime.now());
            notifications.save(n);
        }
        if (prefAllows(recipient, pref == null ? null : pref.getEmail())
                && recipient.getEmail() != null && !recipient.getEmail().isBlank()) {
            try {
                mail.sendHtml(recipient.getEmail(), emailSubject, emailHtml, link);
            } catch (Exception e) {
                log.warn("Project notification email to {} failed: {}", recipient.getEmail(), e.getMessage());
            }
        }
    }

    private boolean prefAllows(User recipient, String channelJson) {
        try {
            if (channelJson == null || channelJson.isBlank()) return true;
            var m = objectMapper.readValue(channelJson, java.util.Map.class);
            Object v = m.get(PREF_KEY);
            return v == null || Boolean.TRUE.equals(v);
        } catch (Exception e) {
            return true;
        }
    }

    private String projectDetailLink(Long id) {
        String base = portalBaseUrl.endsWith("/") ? portalBaseUrl.substring(0, portalBaseUrl.length() - 1) : portalBaseUrl;
        return base + "/projects/" + id;
    }

    /** Branded card built from an admin-editable template (kicker/heading/body/CTA/footer). */
    private String templateCard(String templateName, Map<String, String> vars, String link) {
        return templateService.brandedCard(
                templateService.kicker(templateName, vars),
                templateService.heading(templateName, vars),
                templateService.bodyHtml(templateName, vars),
                templateService.cta(templateName, vars),
                link,
                templateService.footer(templateName, vars));
    }

    private String projectName(Project p) {
        return p.getName() == null ? "" : p.getName();
    }

    private String labelFor(String code) {
        return switch (code == null ? "" : code) {
            case "NOT_STARTED" -> "Not Started";
            case "IN_PROGRESS" -> "In Progress";
            case "ON_HOLD" -> "On Hold";
            case "COMPLETED" -> "Completed";
            case "ARCHIVED" -> "Archived";
            default -> code;
        };
    }

    private String companyName(Project p) {
        return p.getCompany() == null ? "the customer company" : p.getCompany().getName();
    }

    private String displayName(User u) {
        return u == null ? null : u.getFirstName() + " " + (u.getLastName() == null ? "" : u.getLastName());
    }

    private String firstName(User u) {
        return u == null || u.getFirstName() == null ? "there" : u.getFirstName();
    }
}
