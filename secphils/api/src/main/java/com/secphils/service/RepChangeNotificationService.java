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
 * Authorized-representative reassignment fan-out. When staff point a company's
 * rep at a different client, the obligation moves silently — the new rep
 * inherits the Review &amp; complete card and the old one loses it, with no
 * event in the wizard-less route to announce it. This service closes that gap:
 * the NEW rep is told (with the count of projects awaiting their review), the
 * OLD rep is released explicitly, and the provider staff get an in-app row so
 * the change is visible internally. Every recipient is gated independently by
 * their "authorizedRepChanged" preference (missing pref/key = allowed) on both
 * channels, mirroring the document-upload fan-out. Mail failures are logged,
 * never thrown.
 */
@Service
public class RepChangeNotificationService {

    private static final Logger log = LoggerFactory.getLogger(RepChangeNotificationService.class);
    private static final String PREF_KEY = "authorizedRepChanged";

    private final UserRepository users;
    private final ProjectRepository projects;
    private final NotificationRepository notifications;
    private final NotificationPreferenceRepository preferences;
    private final MailService mail;
    private final EmailTemplateService templateService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String portalBaseUrl;

    public RepChangeNotificationService(UserRepository users,
                                        ProjectRepository projects,
                                        NotificationRepository notifications,
                                        NotificationPreferenceRepository preferences,
                                        MailService mail,
                                        EmailTemplateService templateService,
                                        @Value("${app.invite.base-url:http://localhost:3000}") String portalBaseUrl) {
        this.users = users;
        this.projects = projects;
        this.notifications = notifications;
        this.preferences = preferences;
        this.mail = mail;
        this.templateService = templateService;
        this.portalBaseUrl = portalBaseUrl;
    }

    /**
     * Announce a completed rep reassignment. {@code newRep} gets the
     * "you are the rep now + pending count" message, {@code previousRepId}
     * (when it differs and the user exists) gets the release message, and the
     * provider staff each get an in-app row. Email failures never break the
     * save — MailService swallows transport errors and the in-app row is the
     * durable record.
     */
    @Transactional
    public void onRepChanged(Company company, Long previousRepId, User newRep, Long actorId) {
        String companyName = company.getName() == null ? "your company" : company.getName();
        String projectsLink = portalLink("projects");
        String name = newRep.getFirstName() == null || newRep.getFirstName().isBlank()
                ? newRep.getFullName() : newRep.getFirstName();
        int pending = pendingReviewCount(company.getId());
        String countLabel = pending == 0
                ? "No projects are awaiting your review right now — you'll get one whenever a project needs completing."
                : pending == 1
                        ? "1 project is awaiting your review. Open it from your Projects page, check the details, and mark it complete when everything looks right."
                        : pending + " projects are awaiting your review. Open them from your Projects page, check the details, and mark them complete when everything looks right.";

        // 1) The new representative — email + bell.
        Map<String, String> vars = Map.of(
                "name", name,
                "company", companyName,
                "count", String.valueOf(pending),
                "countLabel", countLabel);
        notifyUser(newRep, actorId, true,
                "You're now the authorized representative — " + companyName,
                "Projects awaiting your review: " + pending,
                "REP_ASSIGNED", company.getId(), projectsLink,
                EmailTemplateService.REP_ASSIGNED, vars);

        // 2) The previous representative — email + bell, only when someone
        //    actually lost the role (first-time assignment has no predecessor).
        if (previousRepId != null && !previousRepId.equals(newRep.getId())) {
            users.findById(previousRepId).ifPresent(prev -> {
                Map<String, String> prevVars = Map.of(
                        "name", prev.getFirstName() == null || prev.getFirstName().isBlank()
                                ? "there" : prev.getFirstName(),
                        "company", companyName,
                        "newRep", newRep.getFullName() == null ? newRep.getEmail() : newRep.getFullName());
                notifyUser(prev, actorId, true,
                        "You're no longer the authorized representative — " + companyName,
                        "The role now belongs to " + (newRep.getFullName() == null ? newRep.getEmail() : newRep.getFullName()) + ".",
                        "REP_REMOVED", company.getId(), projectsLink,
                        EmailTemplateService.REP_REMOVED, prevVars);
            });
        }

        // 3) Provider staff — in-app only (they have the dashboard; the bell
        //    keeps them honest about who represents the customer now).
        for (User u : providerStaff()) {
            if (u.getId().equals(actorId)) continue;
            notifyUser(u, actorId, false,
                    "Authorized representative changed — " + companyName,
                    "New rep: " + (newRep.getFullName() == null ? newRep.getEmail() : newRep.getFullName())
                            + " (" + newRep.getEmail() + ")",
                    "REP_ASSIGNED", company.getId(), projectsLink, null, Map.of());
        }
    }

    /** One recipient: gated bell row + (when {@code withEmail}) gated email. */
    private void notifyUser(User u, Long skipIfSameId, boolean withEmail,
                            String bellTitle, String bellBody, String type,
                            Long companyId, String link, String templateName,
                            Map<String, String> vars) {
        if (u == null || u.getId().equals(skipIfSameId)) return;
        NotificationPreference pref = preferences.findByUserId(u.getId()).orElse(null);
        if (prefAllows(pref == null ? null : pref.getInApp())) {
            Notification n = new Notification();
            User ref = new User();
            ref.setId(u.getId());
            n.setRecipient(ref);
            n.setTitle(bellTitle);
            n.setBody(bellBody);
            n.setType(type);
            n.setEntityType("Company");
            n.setEntityId(companyId);
            n.setIsRead(false);
            n.setCreatedAt(LocalDateTime.now());
            notifications.save(n);
        }
        if (withEmail && templateName != null && u.getEmail() != null && !u.getEmail().isBlank()
                && prefAllows(pref == null ? null : pref.getEmail())) {
            try {
                mail.sendHtml(u.getEmail(),
                        templateService.subject(templateName, vars),
                        templateService.brandedCard(
                                templateService.kicker(templateName, vars),
                                templateService.heading(templateName, vars),
                                templateService.bodyHtml(templateName, vars),
                                templateService.cta(templateName, vars),
                                link,
                                templateService.footer(templateName, vars)),
                        link);
            } catch (Exception e) {
                log.warn("Rep-change email to {} failed: {}", u.getEmail(), e.getMessage());
            }
        }
    }

    /** Reads a stored preference flag; missing rows / bad JSON fall back to default-ON. */
    private boolean prefAllows(String channelJson) {
        try {
            if (channelJson == null || channelJson.isBlank()) return true;
            Map<?, ?> m = objectMapper.readValue(channelJson, Map.class);
            Object v = m.get(PREF_KEY);
            return v == null || Boolean.TRUE.equals(v);
        } catch (Exception e) {
            return true;
        }
    }

    /** Projects that still need the rep to review & complete them. */
    private int pendingReviewCount(Long companyId) {
        int n = 0;
        for (Project p : projects.findByCompanyId(companyId)) {
            String s = p.getStatus() == null ? "" : p.getStatus();
            if (!s.equals("COMPLETED") && !s.equals("ARCHIVED")) n++;
        }
        return n;
    }

    private List<User> providerStaff() {
        return users.findAll().stream()
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                .filter(u -> "ADMIN".equals(u.getRole()) || "USER".equals(u.getRole()))
                .toList();
    }

    private String portalLink(String path) {
        String base = portalBaseUrl.endsWith("/")
                ? portalBaseUrl.substring(0, portalBaseUrl.length() - 1) : portalBaseUrl;
        return base + "/" + path;
    }
}
