package com.secphils.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secphils.entity.Company;
import com.secphils.entity.Document;
import com.secphils.entity.Message;
import com.secphils.entity.Notification;
import com.secphils.entity.NotificationPreference;
import com.secphils.entity.Project;
import com.secphils.entity.User;
import com.secphils.policy.DisplayNamePolicy;
import com.secphils.repository.MessageRepository;
import com.secphils.repository.NotificationPreferenceRepository;
import com.secphils.repository.NotificationRepository;
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
 * Document-deletion-request fan-out. A client can ask SECPhils to remove a
 * file instead of composing the message by hand:
 * {@code POST /documents/{id}/deletion-request}. The request IS a message —
 * a real row in the project conversation (so the thread stays the record; no
 * fake "pending" state is invented) — and this service delivers it to both
 * audiences:
 *
 * <ul>
 *   <li><b>Company teammates</b> (CLIENT sender, so never internal): exactly
 *       the client-visible branch of {@code MessageController.dispatch} —
 *       {@code MESSAGE} bell + {@code clientMessage} email, gated by their
 *       {@code newMessage} preference. A typed message and a button-requested
 *       one behave identically for the client's own company.</li>
 *   <li><b>Provider staff</b>: client-visible messages do NOT fan out to
 *       staff (MessageController dispatches internal→staff / client→company
 *       only), so a targeted fan-out is required or the request would die in
 *       a thread nobody is watching — the ReviewNotificationService
 *       precedent. Each staff member gets a {@code DOCUMENT_DELETION_REQUESTED}
 *       bell + the {@code documentDeletionRequested} email, gated by that
 *       preference. The real client name is shown (staff always see real
 *       names; DisplayNamePolicy only collapses PROVIDER identities).</li>
 * </ul>
 *
 * The file itself is untouched until a staff member trashes it (soft delete,
 * restorable) — the request is a signal, not an action. Mail failures are
 * logged, never thrown.
 */
@Service
public class DocumentDeletionRequestService {

    private static final Logger log = LoggerFactory.getLogger(DocumentDeletionRequestService.class);
    private static final String STAFF_PREF_KEY = "documentDeletionRequested";
    private static final String MESSAGE_PREF_KEY = "newMessage";

    private final MessageRepository messages;
    private final UserRepository users;
    private final NotificationRepository notifications;
    private final NotificationPreferenceRepository preferences;
    private final MailService mail;
    private final EmailTemplateService templateService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String portalBaseUrl;

    public DocumentDeletionRequestService(MessageRepository messages,
                                          UserRepository users,
                                          NotificationRepository notifications,
                                          NotificationPreferenceRepository preferences,
                                          MailService mail,
                                          EmailTemplateService templateService,
                                          @Value("${app.invite.base-url:http://localhost:3000}") String portalBaseUrl) {
        this.messages = messages;
        this.users = users;
        this.notifications = notifications;
        this.preferences = preferences;
        this.mail = mail;
        this.templateService = templateService;
        this.portalBaseUrl = portalBaseUrl;
    }

    /** Persists the request message and fans it out to teammates + staff. */
    @Transactional
    public Message requestDeletion(Document doc, Project project, User requester,
                                   String note, Long actorId) {
        String docTitle = doc.getTitle() == null || doc.getTitle().isBlank()
                ? (doc.fileName() == null ? "a document" : doc.fileName()) : doc.getTitle();
        Company company = project.getCompany();
        String companyName = company == null || company.getName() == null ? "the project" : company.getName();

        // 1) The request as a thread message (the durable, visible record).
        String body = "Request to delete document: " + docTitle
                + (note == null || note.isBlank() ? "" : " — " + note.trim());
        Message m = new Message();
        m.setProject(project);
        m.setSender(requester);
        m.setBody(body);
        m.setVisibility("CLIENT");
        m.setCreatedAt(LocalDateTime.now());
        m = messages.save(m);

        String requesterName = requester.getFullName() == null || requester.getFullName().isBlank()
                ? requester.getEmail() : requester.getFullName();

        // 2) Company teammates — identical to a hand-typed client message.
        // DisplayNamePolicy collapses PROVIDER senders to the brand; a CLIENT
        // sender keeps their real name (client identity is never masked — same
        // as MessageController's client-visible dispatch branch). Reply-To is
        // the requester's own address so a teammate can answer by mail.
        String senderDisplay = DisplayNamePolicy.nameFor(requester);
        String msgTitle = "New message from " + senderDisplay
                + " · " + (project.getName() == null ? "" : project.getName());
        String inboxLink = portalLink("messages");
        String replyTo = requester.getEmail() == null || requester.getEmail().isBlank()
                ? DisplayNamePolicy.NO_REPLY_EMAIL : requester.getEmail();
        if (company != null && company.getId() != null) {
            for (User u : users.findByCompanyIdAndIsActiveTrue(company.getId())) {
                if (u.getId().equals(actorId)) continue; // requester already knows
                NotificationPreference pref = preferences.findByUserId(u.getId()).orElse(null);
                boolean inApp = prefAllows(pref == null ? null : pref.getInApp(), MESSAGE_PREF_KEY);
                boolean email = prefAllows(pref == null ? null : pref.getEmail(), MESSAGE_PREF_KEY);
                if (inApp) {
                    saveBell(u, msgTitle, body, "MESSAGE", "Message", m.getId());
                }
                if (email && u.getEmail() != null && !u.getEmail().isBlank()) {
                    Map<String, String> vars = Map.of(
                            "sender", senderDisplay,
                            "project", project.getName() == null ? "" : project.getName(),
                            "body", body);
                    sendMail(u, EmailTemplateService.CLIENT_MESSAGE, vars, inboxLink,
                            replyTo);
                }
            }
        }

        // 3) Provider staff — targeted (client messages never fan out to staff).
        String projectLink = portalLink("projects/" + project.getId());
        String noteVar = note == null || note.isBlank() ? ""
                : " They added a note: \u201c" + note.trim() + "\u201d.";
        String staffBellBody = requesterName + " (" + companyName + ") asked to remove \u201c"
                + docTitle + "\u201d." + noteVar;
        for (User u : providerStaff()) {
            if (u.getId().equals(actorId)) continue;
            NotificationPreference pref = preferences.findByUserId(u.getId()).orElse(null);
            Map<String, String> vars = Map.of(
                    "name", firstName(u),
                    "requester", requesterName,
                    "company", companyName,
                    "document", docTitle,
                    "project", project.getName() == null ? "" : project.getName(),
                    "note", noteVar);
            if (prefAllows(pref == null ? null : pref.getInApp(), STAFF_PREF_KEY)) {
                saveBell(u, "Deletion requested: " + docTitle, staffBellBody,
                        "DOCUMENT_DELETION_REQUESTED", "Document", doc.getId());
            }
            if (prefAllows(pref == null ? null : pref.getEmail(), STAFF_PREF_KEY)
                    && u.getEmail() != null && !u.getEmail().isBlank()) {
                sendMail(u, EmailTemplateService.DOCUMENT_DELETION_REQUESTED, vars, projectLink,
                        DisplayNamePolicy.NO_REPLY_EMAIL);
            }
        }
        return m;
    }

    private void saveBell(User recipient, String title, String body, String type,
                          String entityType, Long entityId) {
        Notification n = new Notification();
        User ref = new User();
        ref.setId(recipient.getId());
        n.setRecipient(ref);
        n.setTitle(title);
        n.setBody(body);
        n.setType(type);
        n.setEntityType(entityType);
        n.setEntityId(entityId);
        n.setIsRead(false);
        n.setCreatedAt(LocalDateTime.now());
        notifications.save(n);
    }

    private void sendMail(User u, String templateName, Map<String, String> vars,
                          String link, String replyTo) {
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
                    link, replyTo);
        } catch (Exception e) {
            log.warn("Deletion-request email to {} failed: {}", u.getEmail(), e.getMessage());
        }
    }

    /** Reads a stored preference flag; missing rows / bad JSON fall back to default-ON. */
    private boolean prefAllows(String channelJson, String key) {
        try {
            if (channelJson == null || channelJson.isBlank()) return true;
            Map<?, ?> m = objectMapper.readValue(channelJson, Map.class);
            Object v = m.get(key);
            return v == null || Boolean.TRUE.equals(v);
        } catch (Exception e) {
            return true;
        }
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

    private String firstName(User u) {
        return u == null || u.getFirstName() == null || u.getFirstName().isBlank()
                ? "there" : u.getFirstName();
    }
}
