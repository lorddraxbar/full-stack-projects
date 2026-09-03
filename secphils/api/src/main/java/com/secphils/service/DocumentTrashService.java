package com.secphils.service;

import com.secphils.common.ApiException;
import com.secphils.common.AuditService;
import com.secphils.entity.Document;
import com.secphils.entity.Project;
import com.secphils.entity.User;
import com.secphils.repository.DocumentCommentRepository;
import com.secphils.repository.DocumentRepository;
import com.secphils.repository.MessageRepository;
import com.secphils.repository.ProjectRepository;
import com.secphils.repository.UserRepository;
import com.secphils.policy.RetentionPolicy;
import com.secphils.security.AuthUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Document trash (soft delete).
 *
 * The trash is a pure database concept: {@code documents.deleted_at} is stamped
 * on "delete" and cleared on "restore". The S3 object is deliberately NOT
 * moved — it stays under the project's storage prefix, so the existing project
 * archive/restore/hard-delete lifecycle keeps owning the object exactly as it
 * does for live documents (a project hard-delete sweeps the prefix and takes
 * any trashed object with it).
 *
 *  delete:     stamp deleted_at + deleted_by. The row, its comments, and the
 *              object all survive. Hidden from the live list, the detail
 *              Documents tab, and all clients.
 *
 *  restore:    clear the stamps. Fully reversible — nothing was moved.
 *
 *  hardDeleteOnePublic: one trashed document, password-gated (the same
 *              re-auth rule as the sibling /hard endpoints). Removes the row
 *              and comments; deletes the S3 object UNLESS it is still
 *              referenced by a live message attachment (message uploads store
 *              one object shared with the auto-created document row) — then
 *              the object is kept for the message download path.
 *
 *  hardDeleteAll: empties the trash. Any provider role (USER or ADMIN) — the
 *              acting user must re-authenticate with their own account
 *              password. Non-admins purge only their own company's trash.
 *
 *  purgeExpired: sweep (DocumentAutoPurger, hourly) of trashed documents
 *              older than the admin-configurable retention window. No
 *              password — the window is what bounds it. Same shared-object
 *              guard.
 */
@Service
public class DocumentTrashService {

    private static final Logger log = LoggerFactory.getLogger(DocumentTrashService.class);

    private final DocumentRepository documents;
    private final DocumentCommentRepository comments;
    private final MessageRepository messages;
    private final ProjectRepository projects;
    private final UserRepository users;
    private final S3StorageService s3;
    private final AuditService auditService;
    private final PasswordEncoder passwordEncoder;
    private final RetentionPolicy retention;

    public DocumentTrashService(DocumentRepository documents,
                                DocumentCommentRepository comments,
                                MessageRepository messages,
                                ProjectRepository projects,
                                UserRepository users,
                                S3StorageService s3,
                                AuditService auditService,
                                PasswordEncoder passwordEncoder,
                                RetentionPolicy retention) {
        this.documents = documents;
        this.comments = comments;
        this.messages = messages;
        this.projects = projects;
        this.users = users;
        this.s3 = s3;
        this.auditService = auditService;
        this.passwordEncoder = passwordEncoder;
        this.retention = retention;
    }

    // --------------------------------------------------------------- delete

    @Transactional
    public Document delete(AuthUser actor, Long id) {
        requireStaff(actor);
        Document doc = loadVisible(actor, id);
        if (doc.getDeletedAt() != null) {
            throw ApiException.conflict("Document is already in the trash");
        }
        doc.setDeletedAt(LocalDateTime.now());
        doc.setDeletedBy(users.findById(actor.id()).orElse(null));
        documents.save(doc);
        auditService.audit(actor, "DOCUMENT_DELETE", "Document", doc.getId(),
                "Title: " + doc.getTitle() + " (moved to trash)", null);
        log.info("Trashed document {} ({}) by {}", doc.getId(), doc.getTitle(), actor.email());
        return doc;
    }

    // -------------------------------------------------------------- restore

    @Transactional
    public Document restore(AuthUser actor, Long id) {
        requireStaff(actor);
        Document doc = loadVisible(actor, id);
        if (doc.getDeletedAt() == null) {
            throw ApiException.badRequest("Document is not in the trash");
        }
        doc.setDeletedAt(null);
        doc.setDeletedBy(null);
        documents.save(doc);
        auditService.audit(actor, "DOCUMENT_RESTORE", "Document", doc.getId(),
                "Title: " + doc.getTitle(), null);
        log.info("Restored document {} ({}) by {}", doc.getId(), doc.getTitle(), actor.email());
        return doc;
    }

    // ------------------------------------------------- one (password-gated)

    @Transactional
    public void hardDeleteOnePublic(AuthUser actor, Document doc, String password) {
        requireStaff(actor);
        requirePassword(actor, password);
        hardDeleteOne(actor, doc, false);
    }

    // ------------------------------------------------------------ empty all

    @Transactional
    public int hardDeleteAll(AuthUser actor, String password) {
        requireStaff(actor);
        requirePassword(actor, password);
        List<Document> trashed;
        if (actor.isAdmin()) {
            trashed = documents.findByDeletedAtIsNotNull();
        } else if (actor.getCompanyId() == null) {
            return 0;
        } else {
            java.util.Set<Long> projectIds = projects.findByCompanyId(actor.getCompanyId()).stream()
                    .map(Project::getId).collect(java.util.stream.Collectors.toSet());
            trashed = projectIds.isEmpty()
                    ? List.of()
                    : documents.findByDeletedAtIsNotNullAndProjectIdIn(projectIds);
        }
        int purged = 0;
        for (Document doc : trashed) {
            if (hardDeleteOne(actor, doc, false)) purged++;
        }
        log.warn("Emptying trash by {} purged {} document(s)", actor.email(), purged);
        return purged;
    }

    // -------------------------------------------------------------- purger

    public int purgeExpired() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(retention.getDays());
        List<Document> expired = documents.findByDeletedAtBefore(cutoff);
        if (expired.isEmpty()) return 0;
        int purged = 0;
        for (Document doc : expired) {
            if (hardDeleteOne(null, doc, true)) purged++;
        }
        log.warn("Auto-purge removed {} document(s) trashed before {}", purged, cutoff.toLocalDate());
        return purged;
    }

    // --------------------------------------------------------------- helpers

    private boolean hardDeleteOne(AuthUser actor, Document doc, boolean auto) {
        try {
            boolean shared = isObjectSharedWithLiveMessage(doc.getFileUrl());
            if (shared) {
                // Keep the object: a live message attachment reads it. It dies
                // with the project (hard-delete sweeps the prefix) or stays
                // referenced — either way it is not orphaned here.
                log.info("Keeping S3 object for purged document {} — shared with a live message attachment",
                        doc.getId());
            } else {
                deleteObjectBestEffort(doc.getFileUrl());
            }
            comments.deleteAll(comments.findByDocumentIdOrderByCreatedAtAsc(doc.getId()));
            documents.delete(doc);
            auditService.audit(actor,
                    auto ? "DOCUMENT_TRASH_PURGED" : "DOCUMENT_PERMANENT_DELETE",
                    "Document", doc.getId(),
                    "Title: " + doc.getTitle()
                            + (shared ? " (object kept: shared with a live message attachment)" : ""),
                    null);
            return true;
        } catch (RuntimeException e) {
            // Never let one bad row abort the whole sweep.
            log.error("Failed to purge trashed document {}", doc.getId(), e);
            return false;
        }
    }

    private void requireStaff(AuthUser actor) {
        if (actor.isClient()) {
            throw ApiException.forbidden("Clients can view and download documents but cannot modify them");
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

    /** Staff may only touch their company's trash; admin sees everything.
      *  Non-admins get a 404 for other companies — don't leak. */
    private Document loadVisible(AuthUser actor, Long id) {
        Document doc = documents.findById(id).orElseThrow(() -> ApiException.notFound("Document"));
        Long companyId = doc.getProject() != null && doc.getProject().getCompany() != null
                ? doc.getProject().getCompany().getId() : null;
        if (!actor.isAdmin() && (companyId == null || !companyId.equals(actor.getCompanyId()))) {
            throw ApiException.notFound("Document");
        }
        return doc;
    }

    /** True when the object is also referenced by a live message row — message
      *  uploads store the same S3 object in both the message and the
      *  auto-created document row. A present message row is a live one
      *  (messages have no soft delete). */
    private boolean isObjectSharedWithLiveMessage(String url) {
        if (url == null || !url.startsWith("s3://")) return false;
        return !messages.findByAttachmentUrlIn(List.of(url)).isEmpty();
    }

    private void deleteObjectBestEffort(String url) {
        if (url == null || !url.startsWith("s3://")) return; // external links: nothing to delete
        s3.deleteQuietly(url);
    }
}
