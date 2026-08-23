package com.secphils.controller;

import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
import com.secphils.dto.DocumentCommentRequest;
import com.secphils.dto.DocumentCommentResponse;
import com.secphils.dto.DocumentRequest;
import com.secphils.dto.DocumentResponse;
import com.secphils.entity.Document;
import com.secphils.entity.DocumentComment;
import com.secphils.entity.Project;
import com.secphils.entity.User;
import com.secphils.repository.DocumentCommentRepository;
import com.secphils.repository.DocumentRepository;
import com.secphils.repository.ProjectRepository;
import com.secphils.security.AuthUser;
import com.secphils.security.CurrentUser;
import com.secphils.service.S3StorageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.secphils.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Project documents, backed by S3-compatible object storage.
 *
 * <p>Role model (mirrors the announcements module):
 * <ul>
 *   <li><b>CLIENT</b> — sees (and can download) documents of their own company's
 *       projects only. Clients cannot create, edit, or delete documents.</li>
 *   <li><b>USER</b> (staff) — same, scoped to their own company.</li>
 *   <li><b>ADMIN</b> — all companies; may pass {@code ?companyId=} to narrow.</li>
 * </ul>
 *
 * <p>Files live in object storage (configured in Admin → Settings → Object
 * Storage). {@code documents.file_url} holds an {@code s3://bucket/key} URI or,
 * for manually linked files, a plain http(s) URL. Downloads go through
 * {@code /documents/{id}/download} so access control is always enforced by the
 * API, even for private buckets.
 */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final DocumentCommentRepository commentRepository;
    private final ProjectRepository projectRepository;
    private final AuditService auditService;
    private final UserRepository userRepository;
    private final S3StorageService storageService;

    public DocumentController(DocumentRepository documentRepository,
                              DocumentCommentRepository commentRepository,
                              ProjectRepository projectRepository, UserRepository userRepository,
                              AuditService auditService, S3StorageService storageService) {
        this.documentRepository = documentRepository;
        this.commentRepository = commentRepository;
        this.projectRepository = projectRepository;
        this.auditService = auditService;
        this.userRepository = userRepository;
        this.storageService = storageService;
    }

    // ---------- reads (role-scoped) ----------

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<DocumentResponse>> list(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Long companyId) {
        AuthUser actor = CurrentUser.require();
        Set<Long> projectIds = null;

        if (actor.isAdmin()) {
            if (companyId != null) {
                projectIds = projectRepository.findByCompanyId(companyId).stream()
                        .map(Project::getId).collect(java.util.stream.Collectors.toSet());
            }
        } else {
            // staff and clients are locked to their own company
            if (companyId != null && !companyId.equals(actor.getCompanyId())) {
                throw ApiException.forbidden("You can only view documents of your own company");
            }
            if (actor.getCompanyId() == null) {
                return ResponseEntity.ok(List.of());
            }
            projectIds = projectRepository.findByCompanyId(actor.getCompanyId()).stream()
                    .map(Project::getId).collect(java.util.stream.Collectors.toSet());
        }

        List<Document> docs;
        if (projectId != null && category != null) {
            docs = documentRepository.findByProjectIdAndCategory(projectId, category);
        } else if (projectId != null) {
            docs = documentRepository.findByProjectId(projectId);
        } else if (projectIds != null && !projectIds.isEmpty()) {
            docs = documentRepository.findByProjectIdIn(projectIds);
        } else if (projectIds != null) {
            return ResponseEntity.ok(List.of()); // company with no projects yet
        } else {
            docs = documentRepository.findAll();
        }

        // Defense in depth: even when a filter was given, never leak a document
        // from a company the actor cannot see.
        final Set<Long> allowed = projectIds;
        if (allowed != null) {
            docs = docs.stream().filter(d -> d.getProject() != null && allowed.contains(d.getProject().getId())).toList();
        }
        return ResponseEntity.ok(docs.stream().map(DocumentResponse::from).toList());
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<DocumentResponse> get(@PathVariable Long id) {
        AuthUser actor = CurrentUser.require();
        Document doc = documentRepository.findById(id).orElseThrow(() -> ApiException.notFound("Document"));
        requireVisibleTo(actor, doc.getProject().getCompany().getId());
        return ResponseEntity.ok(DocumentResponse.from(doc));
    }

    /**
     * Streams the file. S3-backed files are proxied through the API (works for
     * private buckets); plain http(s) references redirect to the source.
     */
    @GetMapping("/{id}/download")
    @Transactional(readOnly = true)
    public ResponseEntity<?> download(@PathVariable Long id) {
        AuthUser actor = CurrentUser.require();
        Document doc = documentRepository.findById(id).orElseThrow(() -> ApiException.notFound("Document"));
        requireVisibleTo(actor, doc.getProject().getCompany().getId());
        String url = doc.getFileUrl();
        if (url == null || url.isBlank()) {
            throw ApiException.badRequest("This document has no file attached");
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
        }
        byte[] bytes = storageService.download(url);
        String name = displayName(url, doc.getTitle());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name.replace("\"", "") + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(bytes.length)
                .body(bytes);
    }

    // ---------- writes (staff + admin only) ----------

    /** Metadata-only creation: links an external file by URL (no bytes stored). */
    @PostMapping
    @Transactional
    public ResponseEntity<DocumentResponse> create(@Valid @RequestBody DocumentRequest req,
                                                   HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        requireStaff(actor);
        Document doc = new Document();
        apply(doc, req);
        if (doc.getFileUrl() != null && doc.getFileUrl().isBlank()) doc.setFileUrl(null);
        doc.setUploader(userRepository.findById(actor.id())
                .orElseThrow(() -> ApiException.notFound("User")));
        doc.setUploadedAt(LocalDateTime.now());
        doc = documentRepository.save(doc);
        auditService.audit(actor, "DOCUMENT_CREATE", "Document", doc.getId(), "Title: " + doc.getTitle(), http);
        return ResponseEntity.status(HttpStatus.CREATED).body(DocumentResponse.from(doc));
    }

    /**
     * Multipart upload: the file bytes go straight to S3 and the document row
     * stores the resulting s3:// reference. If the DB insert fails after a
     * successful S3 put, the orphaned object is removed best-effort.
     */
    @PostMapping("/upload")
    @Transactional
    public ResponseEntity<DocumentResponse> upload(
            @RequestParam("project") Long projectId,
            @RequestParam("title") String title,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String description,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest http) throws IOException {
        AuthUser actor = CurrentUser.require();
        requireStaff(actor);
        if (title == null || title.isBlank()) throw ApiException.badRequest("Title is required");
        if (file == null || file.isEmpty()) throw ApiException.badRequest("No file was uploaded");

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> ApiException.notFound("Project"));
        if (!actor.isAdmin() && !project.getCompany().getId().equals(actor.getCompanyId())) {
            throw ApiException.forbidden("You can only add documents to projects of your own company");
        }

        S3StorageService.StorageConfig cfg = storageService.currentConfig();
        if (!cfg.isConfigured()) {
            throw ApiException.badRequest("Object storage is not configured yet — complete Admin → Settings → Object Storage");
        }

        byte[] bytes = file.getBytes();
        String s3Uri = storageService.upload(cfg, bytes, file.getOriginalFilename(), file.getContentType());
        try {
            Document doc = new Document();
            doc.setProject(project);
            doc.setTitle(title.trim());
            doc.setDescription(description == null ? null : description.trim());
            if (category != null && !category.isBlank()) doc.setCategory(category.trim());
            doc.setFileUrl(s3Uri);
            doc.setFileSize((long) bytes.length);
            doc.setUploader(userRepository.findById(actor.id())
                    .orElseThrow(() -> ApiException.notFound("User")));
            doc.setUploadedAt(LocalDateTime.now());
            doc = documentRepository.save(doc);
            auditService.audit(actor, "DOCUMENT_UPLOAD", "Document", doc.getId(),
                    "Title: " + doc.getTitle() + " (" + bytes.length + " bytes)", http);
            return ResponseEntity.status(HttpStatus.CREATED).body(DocumentResponse.from(doc));
        } catch (RuntimeException e) {
            storageService.deleteQuietly(s3Uri); // don't leak an orphaned object
            throw e;
        }
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<DocumentResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody DocumentRequest req,
                                                   HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        requireStaff(actor);
        Document doc = documentRepository.findById(id).orElseThrow(() -> ApiException.notFound("Document"));
        Project target = projectRepository.findById(req.projectId())
                .orElseThrow(() -> ApiException.notFound("Project"));
        if (!actor.isAdmin() && !target.getCompany().getId().equals(actor.getCompanyId())) {
            throw ApiException.forbidden("You can only edit documents of your own company");
        }
        requireVisibleTo(actor, target.getCompany().getId());

        String previousUrl = doc.getFileUrl();
        apply(doc, req);
        if (doc.getFileUrl() != null && doc.getFileUrl().isBlank()) doc.setFileUrl(null);
        if (previousUrl != null && !previousUrl.equals(doc.getFileUrl())) {
            storageService.deleteQuietly(previousUrl); // replaced — drop the old object
        }
        doc = documentRepository.save(doc);
        auditService.audit(actor, "DOCUMENT_UPDATE", "Document", doc.getId(), "Title: " + doc.getTitle(), http);
        return ResponseEntity.ok(DocumentResponse.from(doc));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        requireStaff(actor);
        Document doc = documentRepository.findById(id).orElseThrow(() -> ApiException.notFound("Document"));
        requireVisibleTo(actor, doc.getProject().getCompany().getId());
        commentRepository.deleteAll(commentRepository.findByDocumentIdOrderByCreatedAtAsc(id));
        documentRepository.delete(doc);
        storageService.deleteQuietly(doc.getFileUrl()); // best-effort S3 cleanup
        auditService.audit(actor, "DOCUMENT_DELETE", "Document", id, "Title: " + doc.getTitle(), http);
        return ResponseEntity.noContent().build();
    }

    // ---------- comments ----------

    @GetMapping("/{id}/comments")
    @Transactional(readOnly = true)
    public ResponseEntity<List<DocumentCommentResponse>> listComments(@PathVariable Long id) {
        AuthUser actor = CurrentUser.require();
        Document doc = documentRepository.findById(id).orElseThrow(() -> ApiException.notFound("Document"));
        requireVisibleTo(actor, doc.getProject().getCompany().getId());
        return ResponseEntity.ok(
                commentRepository.findByDocumentIdOrderByCreatedAtAsc(id).stream()
                        .map(DocumentCommentResponse::from).toList());
    }

    @PostMapping("/{id}/comments")
    @Transactional
    public ResponseEntity<DocumentCommentResponse> addComment(@PathVariable Long id,
                                                              @Valid @RequestBody DocumentCommentRequest req,
                                                              HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        Document doc = documentRepository.findById(id).orElseThrow(() -> ApiException.notFound("Document"));
        requireVisibleTo(actor, doc.getProject().getCompany().getId());
        DocumentComment comment = new DocumentComment();
        comment.setDocument(doc);
        comment.setUser(userRepository.findById(actor.id())
                .orElseThrow(() -> ApiException.notFound("User")));
        comment.setComment(req.comment());
        comment.setCreatedAt(LocalDateTime.now());
        comment = commentRepository.save(comment);
        auditService.audit(actor, "DOCUMENT_COMMENT", "DocumentComment", comment.getId(),
                "Document: " + id, http);
        return ResponseEntity.status(HttpStatus.CREATED).body(DocumentCommentResponse.from(comment));
    }

    // ---------- helpers ----------

    private void requireStaff(AuthUser actor) {
        if (actor.isClient()) {
            throw ApiException.forbidden("Clients can view and download documents but cannot modify them");
        }
    }

    /** Clients/staff may only touch documents of their own company; admin is unrestricted. */
    private void requireVisibleTo(AuthUser actor, Long companyId) {
        if (!actor.isAdmin() && !companyId.equals(actor.getCompanyId())) {
            throw ApiException.notFound("Document"); // 404, not 403 — don't reveal other companies' data
        }
    }

    /** Best-effort display name for downloads: original name from the S3 key, else the title. */
    private static String displayName(String url, String title) {
        if (url.startsWith("s3://")) {
            String key = url.substring("s3://".length());
            int slash = key.lastIndexOf('/');
            String base = slash >= 0 ? key.substring(slash + 1) : key;
            int sep = base.lastIndexOf("__");
            if (sep >= 0 && sep + 3 < base.length()) return base.substring(sep + 2);
        }
        String t = (title == null || title.isBlank()) ? "document" : title;
        return t.replaceAll("[\"\\\\/\\s]+", "_");
    }

    private void apply(Document doc, DocumentRequest req) {
        Project project = projectRepository.findById(req.projectId())
                .orElseThrow(() -> ApiException.notFound("Project"));
        doc.setProject(project);
        doc.setTitle(req.title());
        doc.setDescription(req.description());
        if (req.category() != null && !req.category().isBlank()) doc.setCategory(req.category());
        doc.setFileUrl(req.fileUrl());
        doc.setFileSize(req.fileSize());
    }
}
