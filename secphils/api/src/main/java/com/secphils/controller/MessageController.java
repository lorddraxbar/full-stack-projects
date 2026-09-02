package com.secphils.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
import com.secphils.dto.MessageRequest;
import com.secphils.dto.MessageResponse;
import com.secphils.entity.Company;
import com.secphils.entity.Document;
import com.secphils.entity.Message;
import com.secphils.entity.Notification;
import com.secphils.entity.NotificationPreference;
import com.secphils.entity.Project;
import com.secphils.policy.DisplayNamePolicy;
import com.secphils.repository.DocumentRepository;
import com.secphils.repository.MessageRepository;
import com.secphils.repository.UserRepository;
import com.secphils.repository.NotificationPreferenceRepository;
import com.secphils.repository.NotificationRepository;
import com.secphils.repository.ProjectRepository;
import com.secphils.security.AuthUser;
import com.secphils.entity.User;
import com.secphils.security.CurrentUser;
import com.secphils.service.MailService;
import com.secphils.service.EmailTemplateService;
import com.secphils.service.S3StorageService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

/**
 * Project-scoped team messaging.
 *
 * <p>{@code GET} lists a project's thread; {@code POST} appends a message as the
 * current user and fans a notification out to the audience — an in-app
 * {@link Notification} row plus a branded email, each gated on the recipient's
 * per-channel {@code newMessage} preference (see {@code NotificationController}
 * defaults; a missing key means "allowed").
 *
 * <p><b>Internal messages.</b> A message may carry {@code visibility =
 * "INTERNAL"} (default {@code "CLIENT"}). Internal messages are:
 * <ul>
 *   <li>creatable only by provider staff (USER/ADMIN roles) — a CLIENT-role
 *       sender is rejected with 403;</li>
 *   <li>never returned to a CLIENT-role user by {@code GET} (the row is
 *       filtered in SQL-free memory, so the client never learns it exists);
 *       attachment downloads are likewise 404 for clients;</li>
 *   <li>fanned out to provider staff only (never to company/client members).</li>
 * </ul>
 * "Internal" here is defined by role, not by the (currently unused)
 * {@code project_team_members} table. Public messages keep today's behaviour:
 * visible to company members + admin, fanned out to company members.
 * Mail failures never break the request (MailService logs only), the author
 * is skipped, and the audit trail records the send.
 */
@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {

    private static final String PREF_KEY = "newMessage";

    private final MessageRepository messageRepository;
    private final ProjectRepository projectRepository;
    private final AuditService auditService;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final MailService mailService;
    private final EmailTemplateService templateService;
    private final S3StorageService storageService;
    private final DocumentRepository documentRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String portalBaseUrl;

    public MessageController(MessageRepository messageRepository,
                             ProjectRepository projectRepository,
                             UserRepository userRepository,
                             NotificationRepository notificationRepository,
                             NotificationPreferenceRepository preferenceRepository,
                             MailService mailService,
                             EmailTemplateService templateService,
                             S3StorageService storageService,
                             DocumentRepository documentRepository,
                             AuditService auditService,
                             @Value("${app.invite.base-url:http://localhost:3000}") String portalBaseUrl) {
        this.messageRepository = messageRepository;
        this.projectRepository = projectRepository;
        this.auditService = auditService;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.mailService = mailService;
        this.templateService = templateService;
        this.storageService = storageService;
        this.documentRepository = documentRepository;
        this.portalBaseUrl = portalBaseUrl;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<MessageResponse>> list(@RequestParam Long projectId) {
        AuthUser actor = CurrentUser.require();
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> ApiException.notFound("Project"));
        requireVisibleTo(actor, project.getCompany().getId());
        List<Message> rows = messageRepository.findByProjectIdOrderByCreatedAtAsc(projectId);
        // Internal messages are invisible to CLIENT-role users — filtered
        // server-side so the client neither sees nor can infer them.
        if (actor.isClient()) {
            rows = rows.stream().filter(m -> !isInternal(m)).toList();
        }
        return ResponseEntity.ok(rows.stream().map(MessageResponse::from).toList());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<MessageResponse> send(@Valid @RequestBody MessageRequest req,
                                                HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        Project project = projectRepository.findById(req.projectId())
                .orElseThrow(() -> ApiException.notFound("Project"));
        requireVisibleTo(actor, project.getCompany().getId());
        Message message = new Message();
        message.setProject(project);
        User sender = userRepository.findById(actor.id())
                .orElseThrow(() -> ApiException.notFound("User"));
        message.setSender(sender);
        message.setBody(req.body());
        message.setVisibility(normalizeVisibility(req.visibility()));
        // Only provider staff may mark a message internal; a client's attempt
        // is rejected (the UI never offers the toggle to them).
        if (actor.isClient() && isInternal(message)) {
            throw ApiException.forbidden("Internal messages are limited to provider staff");
        }
        message.setCreatedAt(LocalDateTime.now());
        message = messageRepository.save(message);
        dispatch(message, project, sender, actor, http);
        auditService.audit(actor, "MESSAGE_SEND", "Message", message.getId(),
                "Project: " + project.getId() + (isInternal(message) ? " [internal]" : ""), http);
        return ResponseEntity.status(HttpStatus.CREATED).body(MessageResponse.from(message));
    }

    /**
     * Multipart upload: the file bytes go to object storage and the message row
     * stores the resulting s3:// reference plus display metadata. If the DB
     * insert fails after a successful S3 put, the orphaned object is removed.
     * A blank body is filled with the file name so the row stays non-empty for
     * file-only messages.
     */
    @PostMapping("/upload")
    @Transactional
    public ResponseEntity<MessageResponse> upload(
            @RequestParam("projectId") Long projectId,
            @RequestParam(required = false) String body,
            @RequestParam(required = false) String visibility,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest http) throws IOException {
        AuthUser actor = CurrentUser.require();
        if (file == null || file.isEmpty()) throw ApiException.badRequest("No file was uploaded");

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> ApiException.notFound("Project"));
        requireVisibleTo(actor, project.getCompany().getId());

        S3StorageService.StorageConfig cfg = storageService.currentConfig();
        if (!cfg.isConfigured()) {
            throw ApiException.badRequest("Object storage is not configured yet — an administrator must configure it in Admin Settings first");
        }

        byte[] bytes = file.getBytes();
        String s3Uri = storageService.upload(cfg, project.getId(), bytes, file.getOriginalFilename(), file.getContentType());
        try {
            User sender = userRepository.findById(actor.id())
                    .orElseThrow(() -> ApiException.notFound("User"));
            Message message = new Message();
            message.setProject(project);
            message.setSender(sender);
            message.setBody(body == null || body.isBlank()
                    ? (file.getOriginalFilename() == null ? "File attached" : file.getOriginalFilename())
                    : body.trim());
            message.setVisibility(normalizeVisibility(visibility));
            if (actor.isClient() && isInternal(message)) {
                throw ApiException.forbidden("Internal messages are limited to provider staff");
            }
            message.setAttachmentUrl(s3Uri);
            message.setAttachmentFileName(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
            message.setAttachmentFileSize((long) bytes.length);
            message.setAttachmentContentType(file.getContentType());
            message.setCreatedAt(LocalDateTime.now());
            message = messageRepository.save(message);
            dispatch(message, project, sender, actor, http);
            // Every message attachment is also saved to Documents so it shows up on
            // the all-files view and is findable by project without scrolling the thread.
            Document doc = new Document();
            doc.setProject(project);
            doc.setTitle(message.getAttachmentFileName());
            doc.setDescription("Attached in a message in project " + project.getId() + ".");
            doc.setFileUrl(s3Uri);
            doc.setFileSize((long) bytes.length);
            doc.setUploader(sender);
            doc.setUploadedAt(LocalDateTime.now());
            doc = documentRepository.save(doc);
            auditService.audit(actor, "MESSAGE_UPLOAD", "Message", message.getId(),
                    "Project: " + project.getId() + " (file: " + message.getAttachmentFileName() + ", " + bytes.length + " bytes); saved as Document " + doc.getId(), http);
            return ResponseEntity.status(HttpStatus.CREATED).body(MessageResponse.from(message));
        } catch (RuntimeException e) {
            storageService.deleteQuietly(s3Uri); // don't leak an orphaned object (message + document roll back)
            throw e;
        }
    }

    /**
     * Authenticated proxy download of a message attachment. S3 refs are
     * proxied (so access control is always enforced); plain http(s) refs
     * redirect to the source.
     */
    @GetMapping("/{id}/download")
    @Transactional(readOnly = true)
    public ResponseEntity<?> download(@PathVariable Long id) {
        AuthUser actor = CurrentUser.require();
        Message m = messageRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Message"));
        requireVisibleTo(actor, m.getProject().getCompany().getId());
        // Internal attachments are not downloadable by client-role users — 404,
        // same as an unknown id (don't reveal the message exists).
        if (actor.isClient() && isInternal(m)) {
            throw ApiException.notFound("Message");
        }
        String url = m.getAttachmentUrl();
        if (url == null || url.isBlank()) {
            throw ApiException.badRequest("This message has no file attached");
        }
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(url)).build();
        }
        byte[] bytes = storageService.download(url);
        String q = String.valueOf((char) 34); // double-quote, built without a backslash
        String name = (m.getAttachmentFileName() == null || m.getAttachmentFileName().isBlank())
                ? "attachment" : m.getAttachmentFileName().replace(q, "");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + q + name + q)
                .contentType(m.getAttachmentContentType() == null
                        ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(m.getAttachmentContentType()))
                .contentLength(bytes.length)
                .body(bytes);
    }

    // ---------- fan-out (mirrors AnnouncementController.dispatch) ----------

    private boolean prefAllows(User recipient, String channelJson) {
        try {
            if (channelJson == null || channelJson.isBlank()) return true;
            Map<?, ?> m = objectMapper.readValue(channelJson, Map.class);
            Object v = m.get(PREF_KEY);
            return v == null || Boolean.TRUE.equals(v);
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Fans the new message out to the right audience (skipping the sender):
     * <ul>
     *   <li>INTERNAL → provider staff only (USER/ADMIN roles, any company);</li>
     *   <li>CLIENT (default) → active members of the project's company, as before.</li>
     * </ul>
     * Each recipient gets an in-app {@link Notification} plus a branded email,
     * gated on their per-channel {@code newMessage} preference.
     */
    private void dispatch(Message m, Project project, User sender, AuthUser actor, HttpServletRequest http) {
        Company company = project.getCompany();
        if (company == null) return; // no company -> nothing to fan out to

        boolean internal = isInternal(m);
        String title = (internal ? "Internal message from " : "New message from ")
                + DisplayNamePolicy.nameFor(sender) + " · " + project.getName();
        String body = m.getBody() == null ? "" : m.getBody();
        String link = portalBaseUrl.endsWith("/") ? portalBaseUrl + "messages" : portalBaseUrl + "/messages";
        // Internal messages link to the project's conversation (where the team
        // reads them), public ones to the general messages inbox.
        if (internal) {
            String base = portalBaseUrl.endsWith("/") ? portalBaseUrl : portalBaseUrl + "/";
            link = base + "projects/" + project.getId();
        }
        String templateName = internal
                ? EmailTemplateService.INTERNAL_MESSAGE : EmailTemplateService.CLIENT_MESSAGE;
        String senderName = DisplayNamePolicy.nameFor(sender);
        if (senderName == null) senderName = sender.getEmail() == null ? "a teammate" : sender.getEmail();
        Map<String, String> vars = Map.of(
                "sender", senderName,
                "project", project.getName() == null ? "" : project.getName(),
                "body", body);

        List<User> recipients = internal
                ? providerStaffRecipients()
                : userRepository.findByCompanyIdAndIsActiveTrue(company.getId());

        for (User u : recipients) {
            if (u.getId().equals(actor.id())) continue; // sender already knows
            NotificationPreference pref = preferenceRepository.findByUserId(u.getId()).orElse(null);
            boolean inApp = prefAllows(u, pref == null ? null : pref.getInApp());
            boolean email = prefAllows(u, pref == null ? null : pref.getEmail());

            if (inApp) {
                Notification n = new Notification();
                User ref = new User();
                ref.setId(u.getId());
                n.setRecipient(ref);
                n.setTitle(title);
                n.setBody(body);
                n.setType(internal ? "MESSAGE_INTERNAL" : "MESSAGE");
                n.setEntityType("Message");
                n.setEntityId(m.getId());
                n.setIsRead(false);
                n.setCreatedAt(LocalDateTime.now());
                notificationRepository.save(n);
            }
            if (email && u.getEmail() != null && !u.getEmail().isBlank()) {
                mailService.sendHtml(u.getEmail(), templateService.subject(templateName, vars),
                        messageEmail(templateName, vars, link), link, DisplayNamePolicy.emailFor(sender));
            }
        }
    }

    /** Active USER/ADMIN-role users (provider staff), de-duped by id. */
    private List<User> providerStaffRecipients() {
        List<User> staff = new java.util.ArrayList<>(userRepository.findByRoleAndIsActive("USER", true));
        staff.addAll(userRepository.findByRoleAndIsActive("ADMIN", true));
        staff.sort(Comparator.comparing(User::getId));
        return staff;
    }

    /** Card built from the admin-editable template (kicker / heading / body / CTA / footer). */
    private String messageEmail(String templateName, Map<String, String> vars, String link) {
        return templateService.brandedCard(
                templateService.kicker(templateName, vars),
                templateService.heading(templateName, vars),
                templateService.bodyHtml(templateName, vars),
                templateService.cta(templateName, vars),
                link,
                templateService.footer(templateName, vars));
    }

    /** True when the message is an internal (provider-staff-only) one. */
    private static boolean isInternal(Message m) {
        return m != null && "INTERNAL".equalsIgnoreCase(m.getVisibility());
    }

    /** Normalise the incoming visibility value: blank/CLIENT → CLIENT, else INTERNAL. */
    private static String normalizeVisibility(String raw) {
        if (raw == null || raw.isBlank()) return "CLIENT";
        return "INTERNAL".equalsIgnoreCase(raw.trim()) ? "INTERNAL" : "CLIENT";
    }

    /** Clients/staff may only touch messages of their own company; admin is unrestricted. */
    private void requireVisibleTo(AuthUser actor, Long companyId) {
        if (!actor.isAdmin() && !companyId.equals(actor.getCompanyId())) {
            throw ApiException.notFound("Project"); // 404, not 403 — don't reveal other companies' data
        }
    }
}
