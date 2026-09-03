package com.secphils.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secphils.common.ApiException;
import com.secphils.entity.Message;
import com.secphils.entity.Notification;
import com.secphils.entity.NotificationPreference;
import com.secphils.entity.Project;
import com.secphils.entity.User;
import com.secphils.policy.DisplayNamePolicy;
import com.secphils.repository.MessageRepository;
import com.secphils.repository.NotificationPreferenceRepository;
import com.secphils.repository.NotificationRepository;
import com.secphils.repository.ProjectRepository;
import com.secphils.repository.UserRepository;
import com.secphils.security.AuthUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Project archive lifecycle.
 *
 *  archive:    soft delete. Marks the project ARCHIVED (remembering its
 *              previous status), stamps archived_at and delete_at (7 days
 *              out), relocates all S3 objects under
 *              archive/projects/{id}/{timestamp}/, posts a system message in
 *              the project thread, and notifies the project's company
 *              members (in-app + email, honoring each one's
 *              "projectStatusChanged" preference).
 *
 *  restore:    un-archive. Puts the previous status back, moves the S3
 *              objects back to the live prefix, clears the archive
 *              metadata, and notifies the company members.
 *
 *  hardDelete: permanent removal (DB rows + S3 objects). Only admins.
 *              Allowed once delete_at has passed; before the window closes
 *              the requesting admin must re-authenticate with their
 *              account password.
 */
@Service
public class ProjectArchiveService {

    private static final Logger log = LoggerFactory.getLogger(ProjectArchiveService.class);
    private static final String ARCHIVED = "ARCHIVED";
    private static final String PREF_KEY = "projectStatusChanged";
    private static final int HARD_DELETE_WINDOW_DAYS = 7;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private final ProjectRepository projects;
    private final MessageRepository messages;
    private final UserRepository users;
    private final NotificationRepository notifications;
    private final NotificationPreferenceRepository preferences;
    private final S3StorageService s3;
    private final MailService mail;
    private final EmailTemplateService templateService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String portalBaseUrl;

    public ProjectArchiveService(ProjectRepository projects, MessageRepository messages,
                                 UserRepository users, NotificationRepository notifications,
                                 NotificationPreferenceRepository preferences,
                                 S3StorageService s3, MailService mail,
                                 EmailTemplateService templateService,
                                 PasswordEncoder passwordEncoder,
                                 @Value("${app.invite.base-url:http://localhost:3000}") String portalBaseUrl) {
        this.projects = projects;
        this.messages = messages;
        this.users = users;
        this.notifications = notifications;
        this.preferences = preferences;
        this.s3 = s3;
        this.mail = mail;
        this.templateService = templateService;
        this.passwordEncoder = passwordEncoder;
        this.portalBaseUrl = portalBaseUrl;
    }

    // --------------------------------------------------------------- archive

    @Transactional
    public Project archive(AuthUser actor, Long id) {
        Project p = loadManaged(actor, id);
        if (isArchived(p)) {
            throw ApiException.conflict("Project is already archived");
        }

        LocalDateTime now = LocalDateTime.now();
        p.setPreviousStatus(p.getStatus());
        p.setStatus(ARCHIVED);
        p.setArchivedAt(now);
        p.setDeleteAt(now.plusDays(HARD_DELETE_WINDOW_DAYS));
        relocateToArchive(p);
        projects.save(p);

        systemMessage(p, actor,
                "Project was archived by " + actorName(actor)
                        + ". It will be permanently deleted on "
                        + p.getDeleteAt().toLocalDate() + " unless restored earlier.");

        notifyCompany(p, actor,
                "Project archived: " + p.getName(),
                "The project '" + p.getName() + "' has been archived by " + actorName(actor)
                        + ". It will be permanently removed on " + p.getDeleteAt().toLocalDate()
                        + " unless restored earlier.",
                EmailTemplateService.PROJECT_ARCHIVED);
        log.info("Archived project {} ({}), hard-delete at {}", p.getId(), p.getName(), p.getDeleteAt());
        return p;
    }

    // -------------------------------------------------------------- restore

    @Transactional
    public Project restore(AuthUser actor, Long id) {
        Project p = loadManaged(actor, id);
        if (!isArchived(p)) {
            throw ApiException.badRequest("Only archived projects can be restored");
        }

        p.setStatus(p.getPreviousStatus() != null && !p.getPreviousStatus().isBlank()
                ? p.getPreviousStatus() : "NOT_STARTED");
        p.setArchivedAt(null);
        p.setDeleteAt(null);
        p.setPreviousStatus(null);
        relocateFromArchive(p);
        projects.save(p);

        systemMessage(p, actor, "Project was restored by " + actorName(actor) + ".");
        notifyCompany(p, actor,
                "Project restored: " + p.getName(),
                "The project '" + p.getName() + "' has been restored by " + actorName(actor) + ".",
                EmailTemplateService.PROJECT_RESTORED);
        log.info("Restored project {} ({})", p.getId(), p.getName());
        return p;
    }

    // ----------------------------------------------------------- hard delete

    @Transactional
    public void hardDelete(AuthUser actor, Long id, String password) {
        if (!actor.isAdmin()) {
            throw ApiException.forbidden("Only admins can permanently delete projects");
        }
        Project p = loadManaged(actor, id);
        if (!isArchived(p)) {
            throw ApiException.badRequest("Only archived projects can be deleted");
        }

        boolean windowPassed = p.getDeleteAt() != null && !LocalDateTime.now().isBefore(p.getDeleteAt());
        if (!windowPassed) {
            // Force-delete before the window: re-authenticate with the
            // admin's account password.
            User actorUser = users.findById(actor.id())
                    .orElseThrow(() -> ApiException.notFound("User"));
            String hash = actorUser.getPasswordHash();
            if (hash == null || hash.isBlank()) {
                throw ApiException.forbidden("The " + HARD_DELETE_WINDOW_DAYS
                        + "-day archive window hasn't elapsed and this account has no password on file, "
                        + "so it cannot force an early deletion.");
            }
            if (password == null || password.isBlank() || !passwordEncoder.matches(password, hash)) {
                throw ApiException.forbidden("The " + HARD_DELETE_WINDOW_DAYS
                        + "-day archive window hasn't elapsed. Provide your account password to delete immediately.");
            }
        }

        deleteStorage(p);
        // Team members, documents, document comments, reviews and
        // messages fall away via the DB ON DELETE CASCADE on project_id.
        projects.delete(p);
        log.warn("Permanently deleted project {} ({}) by {} (window passed: {})",
                p.getId(), p.getName(), actor.email(), windowPassed);
    }

    // --------------------------------------------------------------- helpers

    private boolean isArchived(Project p) {
        return p.getArchivedAt() != null || ARCHIVED.equals(p.getStatus());
    }

    /** Staff (USER/ADMIN) may manage any project; clients never. Non-admins
      * are scoped to their own company (404, not 403 — don't leak other
     * companies' data). */
    private Project loadManaged(AuthUser actor, Long id) {
        if (actor.isClient()) {
            throw ApiException.forbidden("Clients cannot manage project lifecycle");
        }
        Project p = projects.findById(id).orElseThrow(() -> ApiException.notFound("Project"));
        if (!actor.isAdmin() && p.getCompany() != null
                && p.getCompany().getId() != null
                && !p.getCompany().getId().equals(actor.getCompanyId())) {
            throw ApiException.notFound("Project");
        }
        return p;
    }

    /** Object-storage layout: {folder}projects/{id}/... (folder may be blank). */
    private String projectPrefix(S3StorageService.StorageConfig cfg, Long projectId) {
        String folder = cfg.folder() == null ? "" : cfg.folder().trim()
                .replaceAll("^/+", "").replaceAll("/+$", "");
        return (folder.isEmpty() ? "" : folder + "/") + "projects/" + projectId + "/";
    }

    /** Move the project's live S3 objects into a timestamped archive dir. */
    private void relocateToArchive(Project p) {
        S3StorageService.StorageConfig cfg = s3.currentConfig();
        if (!cfg.isConfigured()) return;
        String prefix = projectPrefix(cfg, p.getId());
        List<String> keys = s3.listKeys(cfg, prefix);
        if (keys.isEmpty()) return;
        String archiveDir = "archive/projects/" + p.getId() + "/"
                + LocalDateTime.now().format(STAMP) + "/";
        for (String key : keys) {
            s3.copyObject(cfg, key, archiveDir + key.substring(prefix.length()));
        }
        p.setArchiveDir(archiveDir);
        for (String key : keys) {
            s3.deleteQuietly("s3://" + cfg.bucket() + "/" + key);
        }
    }

    /** Move archived S3 objects back to the live prefix. */
    private void relocateFromArchive(Project p) {
        if (p.getArchiveDir() == null) return;
        S3StorageService.StorageConfig cfg = s3.currentConfig();
        if (!cfg.isConfigured()) {
            p.setArchiveDir(null);
            return;
        }
        String archiveDir = p.getArchiveDir();
        String prefix = projectPrefix(cfg, p.getId());
        List<String> keys = s3.listKeys(cfg, archiveDir);
        for (String key : keys) {
            s3.copyObject(cfg, key, prefix + key.substring(archiveDir.length()));
        }
        for (String key : keys) {
            s3.deleteQuietly("s3://" + cfg.bucket() + "/" + key);
        }
        p.setArchiveDir(null);
    }

    /** Best-effort: remove every object under the archive dir and the live prefix. */
    private void deleteStorage(Project p) {
        S3StorageService.StorageConfig cfg = s3.currentConfig();
        if (!cfg.isConfigured()) return;
        List<String> keys = new ArrayList<>();
        if (p.getArchiveDir() != null) {
            keys.addAll(s3.listKeys(cfg, p.getArchiveDir()));
        }
        keys.addAll(s3.listKeys(cfg, projectPrefix(cfg, p.getId())));
        for (String key : keys) {
            s3.deleteQuietly("s3://" + cfg.bucket() + "/" + key);
        }
    }

    /** In-project audit-trail entry, posted under the acting staff account. */
    private void systemMessage(Project p, AuthUser actor, String body) {
        users.findById(actor.id()).ifPresent(sender -> {
            Message m = new Message();
            m.setProject(p);
            m.setSender(sender);
            m.setBody(body);
            m.setCreatedAt(LocalDateTime.now());
            messages.save(m);
        });
    }

    /** In-app + email fan-out to the project's company members (skip actor),
     * honoring each member's "projectStatusChanged" preference. The email
     * subject + card come from the admin-editable template {@code templateName};
     * the in-app notification title/body stay the stable system strings. */
    private void notifyCompany(Project p, AuthUser actor, String title, String body, String templateName) {
        if (p.getCompany() == null) return; // no company -> nothing to fan out to
        String link = portalBaseUrl.endsWith("/") ? portalBaseUrl + "projects" : portalBaseUrl + "/projects";
        String projectName = p.getName() == null ? "" : p.getName();
        String actorName = actorName(actor);
        String deleteDate = p.getDeleteAt() != null ? p.getDeleteAt().toLocalDate().toString() : "";
        Map<String, String> vars = Map.of(
                "project", projectName,
                "actor", actorName,
                "deleteDate", deleteDate);
        String subject = templateService.subject(templateName, vars);
        String card = templateService.brandedCard(
                templateService.kicker(templateName, vars),
                templateService.heading(templateName, vars),
                templateService.bodyHtml(templateName, vars),
                templateService.cta(templateName, vars),
                link,
                templateService.footer(templateName, vars));
        for (User u : users.findByCompanyIdAndIsActiveTrue(p.getCompany().getId())) {
            if (u.getId().equals(actor.id())) continue; // actor already knows
            NotificationPreference pref = preferences.findByUserId(u.getId()).orElse(null);
            boolean inApp = prefAllows(pref == null ? null : pref.getInApp());
            boolean email = prefAllows(pref == null ? null : pref.getEmail());
            if (inApp) {
                Notification n = new Notification();
                User ref = new User();
                ref.setId(u.getId());
                n.setRecipient(ref);
                n.setTitle(title);
                n.setBody(body);
                n.setType("PROJECT_STATUS");
                n.setEntityType("Project");
                n.setEntityId(p.getId());
                n.setIsRead(false);
                n.setCreatedAt(LocalDateTime.now());
                notifications.save(n);
            }
            if (email && u.getEmail() != null && !u.getEmail().isBlank()) {
                mail.sendHtml(u.getEmail(), subject, card, link,
                        users.findById(actor.id()).map(DisplayNamePolicy::emailFor).orElse(DisplayNamePolicy.NO_REPLY_EMAIL));
            }
        }
    }

    /** Actor's display name under the policy (staff actor → brand; client actor → real name). */
    private String actorName(AuthUser actor) {
        return users.findById(actor.id()).map(DisplayNamePolicy::nameFor).orElse(actor.email());
    }

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
}
