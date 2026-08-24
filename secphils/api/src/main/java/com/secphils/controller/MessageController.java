package com.secphils.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
import com.secphils.dto.MessageRequest;
import com.secphils.dto.MessageResponse;
import com.secphils.entity.Company;
import com.secphils.entity.Message;
import com.secphils.entity.Notification;
import com.secphils.entity.NotificationPreference;
import com.secphils.entity.Project;
import com.secphils.entity.User;
import com.secphils.repository.MessageRepository;
import com.secphils.repository.NotificationPreferenceRepository;
import com.secphils.repository.NotificationRepository;
import com.secphils.repository.ProjectRepository;
import com.secphils.repository.UserRepository;
import com.secphils.security.AuthUser;
import com.secphils.security.CurrentUser;
import com.secphils.service.MailService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

/**
 * Project-scoped team messaging.
 *
 * <p>{@code GET} lists a project's thread; {@code POST} appends a message as the
 * current user and fans a notification out to every other active member of the
 * project's company — an in-app {@link Notification} row plus a branded email,
 * each gated on the recipient's per-channel {@code newMessage} preference
 * (see {@code NotificationController} defaults; a missing key means "allowed").
 * Mirrors the announcement fan-out: mail failures never break the request
 * (MailService logs only), the author is skipped, and the audit trail records
 * the send.
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
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String portalBaseUrl;

    public MessageController(MessageRepository messageRepository,
                             ProjectRepository projectRepository,
                             UserRepository userRepository,
                             NotificationRepository notificationRepository,
                             NotificationPreferenceRepository preferenceRepository,
                             MailService mailService,
                             AuditService auditService,
                             @Value("${app.invite.base-url:http://localhost:3000}") String portalBaseUrl) {
        this.messageRepository = messageRepository;
        this.projectRepository = projectRepository;
        this.auditService = auditService;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.mailService = mailService;
        this.portalBaseUrl = portalBaseUrl;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<MessageResponse>> list(@RequestParam Long projectId) {
        if (!projectRepository.existsById(projectId)) throw ApiException.notFound("Project");
        return ResponseEntity.ok(
                messageRepository.findByProjectIdOrderByCreatedAtAsc(projectId).stream()
                        .map(MessageResponse::from).toList());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<MessageResponse> send(@Valid @RequestBody MessageRequest req,
                                                HttpServletRequest http) {
        AuthUser actor = CurrentUser.require();
        Project project = projectRepository.findById(req.projectId())
                .orElseThrow(() -> ApiException.notFound("Project"));
        Message message = new Message();
        message.setProject(project);
        User sender = userRepository.findById(actor.id())
                .orElseThrow(() -> ApiException.notFound("User"));
        message.setSender(sender);
        message.setBody(req.body());
        message.setCreatedAt(LocalDateTime.now());
        message = messageRepository.save(message);
        dispatch(message, project, sender, actor, http);
        auditService.audit(actor, "MESSAGE_SEND", "Message", message.getId(), "Project: " + project.getId(), http);
        return ResponseEntity.status(HttpStatus.CREATED).body(MessageResponse.from(message));
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

    /** Fans the new message out to the project's company members (skipping the sender). */
    private void dispatch(Message m, Project project, User sender, AuthUser actor, HttpServletRequest http) {
        Company company = project.getCompany();
        if (company == null) return; // no company -> nothing to fan out to

        String title = "New message from " + sender.getFullName() + " · " + project.getName();
        String body = m.getBody() == null ? "" : m.getBody();
        String link = portalBaseUrl.endsWith("/") ? portalBaseUrl + "messages" : portalBaseUrl + "/messages";

        for (User u : userRepository.findByCompanyIdAndIsActiveTrue(company.getId())) {
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
                n.setType("MESSAGE");
                n.setEntityType("Message");
                n.setEntityId(m.getId());
                n.setIsRead(false);
                n.setCreatedAt(LocalDateTime.now());
                notificationRepository.save(n);
            }
            if (email && u.getEmail() != null && !u.getEmail().isBlank()) {
                mailService.sendHtml(u.getEmail(), "New message from " + sender.getFullName() + " — " + project.getName(),
                        messageEmail(sender, project, body, link), link, sender.getEmail());
            }
        }
    }

    private String messageEmail(User sender, Project project, String body, String link) {
        return "<!DOCTYPE html><html><body style=\"margin:0;padding:0;background:#f4f5f7;\""
                + "font-family:Arial,Helvetica,sans-serif;color:#1f2937;\">"
                + "<div style=\"max-width:560px;margin:32px auto;padding:32px;background:#ffffff;"
                + "border-radius:12px;border:1px solid #e5e7eb;\">"
                + "<p style=\"margin:0 0 8px;font-size:13px;color:#059669;font-weight:bold;\">SecPhils · " + esc(project.getName()) + "</p>"
                + "<h1 style=\"margin:0 0 16px;font-size:18px;font-weight:600;\">New message from " + esc(sender.getFullName()) + "</h1>"
                + "<p style=\"margin:0 0 16px;font-size:14px;line-height:1.6;\">" + esc(body).replace("\n", "<br>") + "</p>"
                + "<p style=\"margin:0 0 8px;font-size:14px;line-height:1.6;\"><a href=\"" + link + "\" style=\"color:#059669;\">Open the conversation →</a></p>"
                + "<p style=\"margin:16px 0 0;font-size:12px;color:#9ca3af;\">You're receiving this as a member of the project's company. Manage your notification preferences in the portal.</p>"
                + "</div></body></html>";
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
