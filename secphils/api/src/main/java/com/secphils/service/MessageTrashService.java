package com.secphils.service;

import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
import com.secphils.entity.Message;
import com.secphils.entity.Project;
import com.secphils.entity.User;
import com.secphils.policy.RetentionPolicy;
import com.secphils.repository.DocumentRepository;
import com.secphils.repository.MessageRepository;
import com.secphils.repository.NotificationRepository;
import com.secphils.repository.ProjectRepository;
import com.secphils.repository.UserRepository;
import com.secphils.security.AuthUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Provider-only message trash (the supported erasure path). A provider staff
 * member — never a client — moves a message to the trash; it disappears from
 * every thread view, preview and count for EVERYONE, and auto-purges after the
 * retention window. Mirrors DocumentTrashService's discipline exactly:
 *
 * <ul>
 *   <li><b>Soft delete first</b> — stamping {@code deleted_at}/{@code deleted_by}
 *       is reversible within the window; a hard purge (password-gated or the
 *       auto-sweep) is the point of no return.</li>
 *   <li><b>Bell rows die with the message</b> — a {@link com.secphils.entity.Notification}
 *       body carries the message text, so leaving stale bells would keep the
 *       erased content readable. Restore does NOT resurrect bells: the thread is
 *       the record, the bell was transient chrome.</li>
 *   <li><b>The attachment object survives a message erase</b> when a document
 *       row mirrors it (message uploads store ONE s3 object in both rows) —
 *       the document is a separate record; erasing the message does not erase
 *       the file. Delete the file explicitly via the document trash.</li>
 *   <li><b>Audit is the accountability trail</b> — MESSAGE_DELETE / RESTORE /
 *       PERMANENT_DELETE / TRASH_PURGED / TRASH_EMPTY with the stated reason.
 *       The message body is deliberately NOT copied into audit details: the
 *       point of an erasure is that the text stops circulating.</li>
 * </ul>
 *
 * Non-admin staff are scoped to their own company's trash (404 otherwise,
 * mirroring the document trash); admin sees everything.
 */
@Service
public class MessageTrashService {

    private static final Logger log = LoggerFactory.getLogger(MessageTrashService.class);

    private final MessageRepository messages;
    private final DocumentRepository documents;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final NotificationRepository notifications;
    private final S3StorageService s3;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final RetentionPolicy retention;

    public MessageTrashService(MessageRepository messages,
                               DocumentRepository documents,
                               ProjectRepository projects,
                               UserRepository users,
                               NotificationRepository notifications,
                               S3StorageService s3,
                               AuditService auditService,
                               PasswordEncoder passwordEncoder,
                               RetentionPolicy retention) {
        this.messages = messages;
        this.documents = documents;
        this.projects = projects;
        this.users = users;
        this.notifications = notifications;
        this.s3 = s3;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
        this.retention = retention;
    }

    // --------------------------------------------------------------- delete

    @Transactional
    public Message delete(AuthUser actor, Long id, String reason) {
        requireStaff(actor);
        Message msg = loadVisible(actor, id);
        if (msg.getDeletedAt() != null) {
            throw ApiException.conflict("Message is already in the trash");
        }
        msg.setDeletedAt(LocalDateTime.now());
        msg.setDeletedBy(users.findById(actor.id()).orElse(null));
        messages.save(msg);
        // The bell rows carried the message text — they go with it (restore
        // does not resurrect them; the thread is the record).
        notifications.deleteByEntityTypeAndEntityId("Message", msg.getId());
        String details = "Project: " + projectName(msg)
                + " · sender: " + (msg.getSender() == null ? "?" : msg.getSender().getEmail())
                + " · reason: " + (reason == null || reason.isBlank() ? "(none given)" : reason.trim());
        auditService.audit(actor, "MESSAGE_DELETE", "Message", msg.getId(), details, null);
        log.info("Trashed message {} (project {}) by {}", msg.getId(), projectName(msg), actor.email());
        return msg;
    }

    // -------------------------------------------------------------- restore

    @Transactional
    public Message restore(AuthUser actor, Long id) {
        requireStaff(actor);
        Message msg = loadVisible(actor, id);
        if (msg.getDeletedAt() == null) {
            throw ApiException.badRequest("Message is not in the trash");
        }
        msg.setDeletedAt(null);
        msg.setDeletedBy(null);
        msg = messages.save(msg);
        auditService.audit(actor, "MESSAGE_RESTORE", "Message", msg.getId(),
                "Project: " + projectName(msg), null);
        log.info("Restored message {} (project {}) by {}", msg.getId(), projectName(msg), actor.email());
        return msg;
    }

    // ----------------------------------------------------------- auto purge

    /** Hourly sweep: messages trashed beyond the retention window are purged
     *  (row + attachment object when not mirrored by a document row). The
     *  window is what bounds the removal — no password. */
    @Transactional
    public int purgeExpired() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retention.getDays());
        List<Message> expired = messages.findByDeletedAtBefore(cutoff);
        if (expired.isEmpty()) return 0;
        int purged = 0;
        for (Message m : expired) {
            if (hardDeleteOne(null, m, true)) purged++;
        }
        log.warn("Message auto-purge removed {} message(s) trashed before {}", purged, cutoff.toLocalDate());
        return purged;
    }

    // ------------------------------------------------------- hard deletion

    public void hardDeleteOnePublic(AuthUser actor, Message msg, String password) {
        requireStaff(actor);
        requirePassword(actor, password);
        Long companyId = companyId(msg);
        if (!actor.isAdmin() && (companyId == null || !companyId.equals(actor.getCompanyId()))) {
            throw ApiException.notFound("Message");
        }
        hardDeleteOne(actor, msg, false);
    }

    @Transactional
    public int hardDeleteAll(AuthUser actor, String password) {
        requireStaff(actor);
        requirePassword(actor, password);
        List<Message> trashed;
        if (actor.isAdmin()) {
            trashed = messages.findWithRefsDeleted();
        } else if (actor.getCompanyId() == null) {
            return 0;
        } else {
            Set<Long> projectIds = projects.findByCompanyId(actor.getCompanyId()).stream()
                    .map(Project::getId).collect(Collectors.toSet());
            trashed = projectIds.isEmpty()
                    ? List.of()
                    : messages.findWithRefsDeleted().stream()
                            .filter(m -> m.getProject() != null && projectIds.contains(m.getProject().getId()))
                            .toList();
        }
        int purged = 0;
        for (Message m : trashed) {
            if (hardDeleteOne(actor, m, false)) purged++;
        }
        log.warn("Emptying message trash by {} purged {} message(s)", actor.email(), purged);
        return purged;
    }

    /** Purge row + (when no document row mirrors it) the S3 object. Never lets
     *  one bad row abort a sweep. */
    private boolean hardDeleteOne(AuthUser actor, Message msg, boolean auto) {
        try {
            boolean mirrored = documentMirrors(msg.getAttachmentUrl());
            if (mirrored) {
                log.info("Keeping S3 object for purged message {} — mirrored by a document row", msg.getId());
            } else if (msg.getAttachmentUrl() != null && msg.getAttachmentUrl().startsWith("s3://")) {
                s3.deleteQuietly(msg.getAttachmentUrl());
            }
            messages.delete(msg);
            auditService.audit(actor,
                    auto ? "MESSAGE_TRASH_PURGED" : "MESSAGE_PERMANENT_DELETE",
                    "Message", msg.getId(),
                    "Project: " + projectName(msg)
                            + (mirrored ? " (attachment object kept: mirrored by a document row)" : ""),
                    null);
            return true;
        } catch (RuntimeException e) {
            log.error("Failed to purge trashed message {}", msg.getId(), e);
            return false;
        }
    }

    // --------------------------------------------------------------- helpers

    /** A document row referencing the same object keeps it alive — the file is
     *  a separate record with its own (document) trash lifecycle. */
    private boolean documentMirrors(String url) {
        if (url == null || url.isBlank()) return false;
        return !documents.findByFileUrl(url).isEmpty();
    }

    /** Erasure is provider staff only — clients can never reach this surface. */
    private void requireStaff(AuthUser actor) {
        if (actor.isClient()) {
            throw ApiException.forbidden("Message removal is limited to SECPhils staff");
        }
    }

    private void requirePassword(AuthUser actor, String password) {
        User actorRow = users.findById(actor.id()).orElseThrow(() -> ApiException.notFound("User"));
        String hash = actorRow.getPasswordHash();
        if (hash == null || hash.isBlank()) {
            throw ApiException.forbidden("This account has no password on file, so it cannot empty the trash");
        }
        if (password == null || password.isBlank() || !passwordEncoder.matches(password, hash)) {
            throw ApiException.forbidden("Password confirmation failed");
        }
    }

    /** Staff may only touch their company's messages; admin sees everything.
     *  Non-admins get a 404 for other companies — don't leak. */
    private Message loadVisible(AuthUser actor, Long id) {
        Message msg = messages.findWithRefsById(id)
                .orElseThrow(() -> ApiException.notFound("Message"));
        Long companyId = companyId(msg);
        if (!actor.isAdmin() && (companyId == null || !companyId.equals(actor.getCompanyId()))) {
            throw ApiException.notFound("Message");
        }
        return msg;
    }

    private Long companyId(Message msg) {
        return msg.getProject() != null && msg.getProject().getCompany() != null
                ? msg.getProject().getCompany().getId() : null;
    }

    private String projectName(Message msg) {
        return msg.getProject() == null ? "?" : msg.getProject().getName();
    }
}
