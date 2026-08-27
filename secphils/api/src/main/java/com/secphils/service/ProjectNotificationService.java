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
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String portalBaseUrl;

    public ProjectNotificationService(ProjectRepository projects,
                                      UserRepository users,
                                      NotificationRepository notifications,
                                      NotificationPreferenceRepository preferences,
                                      MailService mail,
                                      @Value("${app.invite.base-url:http://localhost:3000}") String portalBaseUrl) {
        this.projects = projects;
        this.users = users;
        this.notifications = notifications;
        this.preferences = preferences;
        this.mail = mail;
        this.portalBaseUrl = portalBaseUrl;
    }

    @Transactional
    public void onProjectCreated(Project project, Long actorId) {
        String link = projectDetailLink(project.getId());
        String repName = displayName(project.getCompany().getAuthorizedRep());
        // 1) The customer's authorized rep: review + complete the project.
        deliver(project, project.getCompany().getAuthorizedRep(), actorId,
                "New project submitted — " + project.getName(),
                "New project submitted for " + esc(companyName(project)),
                brandedEmail(
                        "New project submitted — " + esc(project.getName()),
                        "<p>Hi " + esc(firstName(project.getCompany().getAuthorizedRep())) + ",</p>"
                                + "<p><strong>" + esc(companyName(project)) + "</strong> just submitted the project "
                                + "<strong>\"" + esc(project.getName()) + "\"</strong> for review. Please open it in "
                                + "the portal, check the details, and mark it complete when everything looks right.</p>"
                                + ctaButton("Open " + esc(project.getName()), link),
                        "You're receiving this as the authorized representative of the customer company. Manage your notification preferences in the portal."),
                link, "NEW_PROJECT");
        // 2) Provider side: every other active staff user.
        for (User u : activeProviderUsers(project.getCompany())) {
            if (u.getId().equals(actorId)) continue;
            deliver(project, u, actorId,
                    "New project — " + project.getName(),
                    "New project for " + esc(companyName(project)) + " — " + esc(project.getName()),
                    brandedEmail(
                            "New project — " + esc(project.getName()),
                            "<p>Hi " + esc(firstName(u)) + ",</p>"
                                    + "<p>" + esc(companyName(project)) + " submitted a new project, <strong>\""
                                    + esc(project.getName()) + "\"</strong>"
                                    + (repName != null ? ", with " + esc(repName) + " as the authorized representative" : "")
                                    + ". It is waiting for review and completion.</p>"
                                    + inlineLink("View the project", link),
                            "You're receiving this as a member of the SECPhils provider team."),
                    link, "NEW_PROJECT");
        }
    }

    @Transactional
    public void onStatusChanged(Project project, String oldStatus, String newStatus, Long actorId) {
        if (oldStatus == null || oldStatus.equals(newStatus)) return;
        String label = labelFor(newStatus);
        String link = projectDetailLink(project.getId());
        deliver(project, project.getCompany().getAuthorizedRep(), actorId,
                "Project " + label + " — " + project.getName(),
                project.getName() + " is now " + label,
                brandedEmail(
                        "Project " + label + " — " + esc(project.getName()),
                        "<p>Hi " + esc(firstName(project.getCompany().getAuthorizedRep())) + ",</p>"
                                + "<p>The project <strong>\"" + esc(project.getName()) + "\"</strong> ("
                                + esc(companyName(project)) + ") is now <strong>" + esc(label) + "</strong>.</p>"
                                + inlineLink("View the project", link),
                        "You're receiving this as the authorized representative of the customer company. Manage your notification preferences in the portal."),
                link, "PROJECT_STATUS");
        for (User u : activeProviderUsers(project.getCompany())) {
            if (u.getId().equals(actorId)) continue;
            deliver(project, u, actorId,
                    "Project " + label + " — " + project.getName(),
                    project.getName() + " is now " + label,
                    brandedEmail(
                            "Project " + label + " — " + esc(project.getName()),
                            "<p>Hi " + esc(firstName(u)) + ",</p>"
                                    + "<p>" + esc(companyName(project)) + "'s project <strong>\"" + esc(project.getName())
                                    + "\"</strong> is now <strong>" + esc(label) + "</strong>.</p>"
                                    + inlineLink("View the project", link),
                            "You're receiving this as a member of the SECPhils provider team."),
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

    /** Same branded shell the message/archive emails use. */
    private String brandedEmail(String headerTitle, String bodyHtml, String footerNote) {
        return "<!DOCTYPE html><html><body style=\"margin:0;padding:0;background:#f4f5f7;\""
                + "font-family:Arial,Helvetica,sans-serif;color:#1f2937;\">"
                + "<div style=\"max-width:560px;margin:32px auto;padding:32px;background:#ffffff;"
                + "border-radius:12px;border:1px solid #e5e7eb;\">"
                + "<p style=\"margin:0 0 8px;font-size:13px;color:#059669;font-weight:bold;\">SecPhils</p>"
                + "<h1 style=\"margin:0 0 16px;font-size:18px;font-weight:600;\">" + headerTitle + "</h1>"
                + bodyHtml
                + "<p style=\"margin:16px 0 0;font-size:12px;color:#9ca3af;\">" + esc(footerNote) + "</p>"
                + "</div></body></html>";
    }

    private String ctaButton(String label, String link) {
        return "<p style=\"margin:16px 0 0;\"><a href=\"" + link + "\" style=\"display:inline-block;background:#059669;color:#ffffff;"
                + "padding:10px 18px;border-radius:8px;font-weight:bold;text-decoration:none;\">" + label + "</a></p>";
    }

    private String inlineLink(String label, String link) {
        return "<p style=\"margin:16px 0 0;font-size:14px;\"><a href=\"" + link + "\" style=\"color:#059669;text-decoration:underline;\">" + label + " →</a></p>";
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

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
