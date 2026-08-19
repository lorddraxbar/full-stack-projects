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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import com.secphils.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final DocumentCommentRepository commentRepository;
    private final ProjectRepository projectRepository;
    private final AuditService auditService;
    private final UserRepository userRepository;

    public DocumentController(DocumentRepository documentRepository,
                              DocumentCommentRepository commentRepository,
                              ProjectRepository projectRepository, UserRepository userRepository,
                              AuditService auditService) {
        this.documentRepository = documentRepository;
        this.commentRepository = commentRepository;
        this.projectRepository = projectRepository;
        this.auditService = auditService;
        this.userRepository = userRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<DocumentResponse>> list(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String category) {
        List<Document> docs;
        if (projectId != null && category != null) {
            docs = documentRepository.findByProjectIdAndCategory(projectId, category);
        } else if (projectId != null) {
            docs = documentRepository.findByProjectId(projectId);
        } else {
            docs = documentRepository.findAll();
        }
        return ResponseEntity.ok(docs.stream().map(DocumentResponse::from).toList());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<DocumentResponse> create(@Valid @RequestBody DocumentRequest req,
                                                   HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        Document doc = new Document();
        apply(doc, req);
        doc.setUploader(userRepository.findById(actor.id())
                .orElseThrow(() -> ApiException.notFound("User")));
        doc.setUploadedAt(LocalDateTime.now());
        doc = documentRepository.save(doc);
        auditService.audit(actor, "DOCUMENT_CREATE", "Document", doc.getId(), "Title: " + doc.getTitle(), http);
        return ResponseEntity.status(HttpStatus.CREATED).body(DocumentResponse.from(doc));
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<DocumentResponse> get(@PathVariable Long id) {
        Document doc = documentRepository.findById(id).orElseThrow(() -> ApiException.notFound("Document"));
        return ResponseEntity.ok(DocumentResponse.from(doc));
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<DocumentResponse> update(@PathVariable Long id,
                                                   @Valid @RequestBody DocumentRequest req,
                                                   HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        Document doc = documentRepository.findById(id).orElseThrow(() -> ApiException.notFound("Document"));
        apply(doc, req);
        doc = documentRepository.save(doc);
        auditService.audit(actor, "DOCUMENT_UPDATE", "Document", doc.getId(), "Title: " + doc.getTitle(), http);
        return ResponseEntity.ok(DocumentResponse.from(doc));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        Document doc = documentRepository.findById(id).orElseThrow(() -> ApiException.notFound("Document"));
        commentRepository.deleteAll(commentRepository.findByDocumentIdOrderByCreatedAtAsc(id));
        documentRepository.delete(doc);
        auditService.audit(actor, "DOCUMENT_DELETE", "Document", id, "Title: " + doc.getTitle(), http);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/comments")
    @Transactional(readOnly = true)
    public ResponseEntity<List<DocumentCommentResponse>> listComments(@PathVariable Long id) {
        if (!documentRepository.existsById(id)) throw ApiException.notFound("Document");
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
