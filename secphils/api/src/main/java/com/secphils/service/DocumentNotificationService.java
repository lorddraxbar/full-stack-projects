package com.secphils.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secphils.entity.Company;
import com.secphils.entity.Document;
import com.secphils.entity.Notification;
import com.secphils.entity.NotificationPreference;
import com.secphils.entity.Project;
import com.secphils.entity.User;
import com.secphils.policy.DisplayNamePolicy;
import com.secphils.repository.NotificationPreferenceRepository;
import com.secphils.repository.NotificationRepository;
import com.secphils.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Document-upload fan-out. When staff uploads a document to a project, the
 * people allowed to see it hear about it: the project company's active
 * members plus the provider-side staff (the uploader themselves is skipped).
 * Each recipient gets an in-app {@link Notification} row and a branded email,
 * each gated independently by their "documentUploaded" preference (missing
 * preference or key = allowed, same convention as the message/announcement
 * fan-out). The uploader's display name follows {@link DisplayNamePolicy} —
 * provider staff collapse to the brand on client-readable surfaces.
 *
 * Mail failures are logged, never thrown — a down mailbox must not break
 * the upload (the in-app row stays the durable record).
 */
@Service
public class DocumentNotificationService {

    private static final Logger log = LoggerFactory.getLogger(DocumentNotificationService.class);
    private static final String PREF_KEY = "documentUploaded";

    private final UserRepository users;
    private final NotificationRepository notifications;
    private final NotificationPreferenceRepository preferences;
    private final MailService mail;
    private final EmailTemplateService templateService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String portalBaseUrl;

    public DocumentNotificationService(UserRepository users,
                                       NotificationRepository notifications,
                                       NotificationPreferenceRepository preferences,
                                       MailService mail,
                                       EmailTemplateService templateService,
                                       @Value("${app.invite.base-url:http://localhost:3000}") String portalBaseUrl) {
        this.users = users;
        this.notifications = notifications;
        this.preferences = preferences;
        this.mail = mail;
        this.templateService = templateService;
        this.portalBaseUrl = portalBaseUrl;
    }

    @Transactional
    public void onDocumentUploaded(Document doc, Project project, Long actorId) {
        Company company = project.getCompany();
        List<User> recipients = new ArrayList<>();
        if (company != null && company.getId() != null) {
            recipients.addAll(users.findByCompanyIdAndIsActiveTrue(company.getId()));
        }
        recipients.addAll(activeProviderUsers(company));

        String uploaderName = users.findById(actorId)
                .map(DisplayNamePolicy::nameFor).orElse("SECPhils");
        String link = projectDetailLink(project.getId());
        String title = "New document — " + doc.getTitle();
        String body = "Project: " + projectName(project)
                + (company != null ? " — " + company.getName() : "");

        Map<String, String> vars = Map.of(
                "name", "",                     // per-recipient, replaced below
                "document", doc.getTitle() == null ? "" : doc.getTitle(),
                "project", projectName(project),
                "company", company != null && company.getName() != null
                        ? company.getName() : "the project",
                "uploader", uploaderName);

        for (User u : recipients) {
            if (u.getId().equals(actorId)) continue; // uploader already knows
            NotificationPreference pref = preferences.findByUserId(u.getId()).orElse(null);
            Map<String, String> perRecipient = new java.util.HashMap<>(vars);
            perRecipient.put("name", firstName(u));

            if (prefAllows(pref == null ? null : pref.getInApp())) {
                Notification n = new Notification();
                User ref = new User();
                ref.setId(u.getId());
                n.setRecipient(ref);
                n.setTitle(title);
                n.setBody(body);
                n.setType("DOCUMENT_UPLOADED");
                n.setEntityType("Document");
                n.setEntityId(doc.getId());
                n.setIsRead(false);
                n.setCreatedAt(LocalDateTime.now());
                notifications.save(n);
            }
            if (prefAllows(pref == null ? null : pref.getEmail())
                    && u.getEmail() != null && !u.getEmail().isBlank()) {
                try {
                    mail.sendHtml(u.getEmail(),
                            templateService.subject(EmailTemplateService.DOCUMENT_UPLOADED, perRecipient),
                            templateService.brandedCard(
                                    templateService.kicker(EmailTemplateService.DOCUMENT_UPLOADED, perRecipient),
                                    templateService.heading(EmailTemplateService.DOCUMENT_UPLOADED, perRecipient),
                                    templateService.bodyHtml(EmailTemplateService.DOCUMENT_UPLOADED, perRecipient),
                                    templateService.cta(EmailTemplateService.DOCUMENT_UPLOADED, perRecipient),
                                    link,
                                    templateService.footer(EmailTemplateService.DOCUMENT_UPLOADED, perRecipient)),
                            link,
                            // uploads are staff-only; brand-collapse the mail-from
                            DisplayNamePolicy.NO_REPLY_EMAIL);
                } catch (Exception e) {
                    log.warn("Document-upload email to {} failed: {}", u.getEmail(), e.getMessage());
                }
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

    /** Provider-side staff (ADMIN/USER) NOT belonging to the customer company. */
    private List<User> activeProviderUsers(Company customerCompany) {
        List<User> out = new ArrayList<>();
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

    private String projectDetailLink(Long id) {
        String base = portalBaseUrl.endsWith("/")
                ? portalBaseUrl.substring(0, portalBaseUrl.length() - 1) : portalBaseUrl;
        return base + "/projects/" + id;
    }

    private String projectName(Project p) {
        return p.getName() == null ? "" : p.getName();
    }

    private String firstName(User u) {
        return u == null || u.getFirstName() == null || u.getFirstName().isBlank()
                ? "there" : u.getFirstName();
    }
}
