package com.secphils.controller;

import com.secphils.common.AuditService;
import com.secphils.common.ApiException;
import com.secphils.dto.NotificationResponse;
import com.secphils.entity.Notification;
import com.secphils.entity.NotificationPreference;
import com.secphils.repository.NotificationPreferenceRepository;
import com.secphils.repository.NotificationRepository;
import com.secphils.security.AuthUser;
import com.secphils.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceRepository preferenceRepository;
    private final AuditService auditService;

    public NotificationController(NotificationRepository notificationRepository,
                                  NotificationPreferenceRepository preferenceRepository,
                                  AuditService auditService) {
        this.notificationRepository = notificationRepository;
        this.preferenceRepository = preferenceRepository;
        this.auditService = auditService;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ResponseEntity<List<NotificationResponse>> list(
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly) {
        AuthUser me = CurrentUser.require();
        List<Notification> items = unreadOnly
                ? notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(me.id())
                : notificationRepository.findByRecipientIdOrderByCreatedAtDesc(me.id());
        return ResponseEntity.ok(items.stream().map(NotificationResponse::from).toList());
    }

    @PatchMapping("/{id}/read")
    @Transactional
    public ResponseEntity<NotificationResponse> markRead(@PathVariable Long id, HttpServletRequest http) {
        AuthUser me = CurrentUser.require();
        Notification n = notificationRepository.findById(id)
                .filter(x -> x.getRecipient() != null && me.id().equals(x.getRecipient().getId()))
                .orElseThrow(() -> ApiException.notFound("Notification"));
        n.setIsRead(true);
        notificationRepository.save(n);
        auditService.audit(me, "NOTIFICATION_READ", "Notification", n.getId(), null, http);
        return ResponseEntity.ok(NotificationResponse.from(n));
    }

    @PatchMapping("/read-all")
    @Transactional
    public ResponseEntity<Map<String, Object>> markAllRead(HttpServletRequest http) {
        AuthUser me = CurrentUser.require();
        List<Notification> unread = notificationRepository
                .findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(me.id());
        unread.forEach(n -> n.setIsRead(true));
        notificationRepository.saveAll(unread);
        auditService.audit(me, "NOTIFICATION_READ_ALL", "Notification", null,
                "Count: " + unread.size(), http);
        return ResponseEntity.ok(Map.of("updated", unread.size()));
    }

    @GetMapping("/preferences")
    @Transactional(readOnly = true)
    public ResponseEntity<Map<String, Object>> getPreferences() {
        AuthUser me = CurrentUser.require();
        NotificationPreference pref = preferenceRepository.findByUserId(me.id()).orElseGet(() -> {
            NotificationPreference p = new NotificationPreference();
            p.setUserId(me.id());
            return p;
        });
        return ResponseEntity.ok(Map.of(
                "taskAssigned", pref.getTaskAssigned(),
                "projectCreated", pref.getProjectCreated(),
                "newMessage", pref.getNewMessage(),
                "documentRequest", pref.getDocumentRequest(),
                "reviewSubmitted", pref.getReviewSubmitted(),
                "announcement", pref.getAnnouncement(),
                "statusChange", pref.getStatusChange()));
    }

    @PutMapping("/preferences")
    @Transactional
    public ResponseEntity<Map<String, Object>> updatePreferences(@RequestBody Map<String, Boolean> body,
                                                                 HttpServletRequest http) {
        AuthUser me = CurrentUser.require();
        NotificationPreference pref = preferenceRepository.findByUserId(me.id()).orElseGet(() -> {
            NotificationPreference p = new NotificationPreference();
            p.setUserId(me.id());
            return p;
        });
        if (body.containsKey("taskAssigned")) pref.setTaskAssigned(body.get("taskAssigned"));
        if (body.containsKey("projectCreated")) pref.setProjectCreated(body.get("projectCreated"));
        if (body.containsKey("newMessage")) pref.setNewMessage(body.get("newMessage"));
        if (body.containsKey("documentRequest")) pref.setDocumentRequest(body.get("documentRequest"));
        if (body.containsKey("reviewSubmitted")) pref.setReviewSubmitted(body.get("reviewSubmitted"));
        if (body.containsKey("announcement")) pref.setAnnouncement(body.get("announcement"));
        if (body.containsKey("statusChange")) pref.setStatusChange(body.get("statusChange"));
        pref.setUpdatedAt(LocalDateTime.now());
        preferenceRepository.save(pref);
        auditService.audit(me, "NOTIFICATION_PREF_UPDATE", "NotificationPreference", me.id(), null, http);
        return ResponseEntity.ok(Map.of(
                "taskAssigned", pref.getTaskAssigned(),
                "projectCreated", pref.getProjectCreated(),
                "newMessage", pref.getNewMessage(),
                "documentRequest", pref.getDocumentRequest(),
                "reviewSubmitted", pref.getReviewSubmitted(),
                "announcement", pref.getAnnouncement(),
                "statusChange", pref.getStatusChange()));
    }
}
