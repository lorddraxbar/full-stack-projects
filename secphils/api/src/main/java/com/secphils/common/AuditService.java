package com.secphils.common;

import com.secphils.entity.AuditLog;
import com.secphils.entity.Notification;
import com.secphils.entity.User;
import com.secphils.repository.AuditLogRepository;
import com.secphils.repository.NotificationRepository;
import com.secphils.security.AuthUser;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes audit trail entries and notifications for significant actions.
 * Runs in a REQUIRES_NEW transaction so audit/notify writes survive even if
 * the caller's transaction rolls back.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final NotificationRepository notificationRepository;

    public AuditService(AuditLogRepository auditLogRepository, NotificationRepository notificationRepository) {
        this.auditLogRepository = auditLogRepository;
        this.notificationRepository = notificationRepository;
    }

    /** Detached reference so we only write the FK, not a new user row. */
    private static User userRef(Long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    /** The details column is JSONB — wrap free-text so the column is always valid JSON. */
    private static String toJson(String details) {
        if (details == null || details.isBlank()) {
            return null;
        }
        String t = details.trim();
        if (t.startsWith("{") || t.startsWith("[")) {
            return t;
        }
        String escaped = t.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
        return "{\"message\":\"" + escaped + "\"}";
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void audit(AuthUser user, String action, String entityType, Long entityId, String details,
                      HttpServletRequest request) {
        AuditLog log = new AuditLog();
        if (user != null) {
            log.setUser(userRef(user.id()));
        }
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setDetails(toJson(details));
        if (request != null) {
            log.setIpAddress(request.getRemoteAddr());
        }
        log.setCreatedAt(LocalDateTime.now());
        auditLogRepository.save(log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notify(Long recipientId, String title, String body, String type, String entityType, Long entityId) {
        Notification n = new Notification();
        n.setRecipient(userRef(recipientId));
        n.setTitle(title);
        n.setBody(body);
        n.setType(type);
        n.setEntityType(entityType);
        n.setEntityId(entityId);
        n.setIsRead(false);
        n.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(n);
    }

    /** Filtered, newest-first audit log query for the admin console. */
    @Transactional(readOnly = true)
    public List<AuditLog> query(String action, Long userId, int limit) {
        Specification<AuditLog> spec = (root, q, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (action != null && !action.isBlank()) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            if (userId != null) {
                predicates.add(cb.equal(root.get("user").get("id"), userId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        int size = Math.max(1, Math.min(limit, 500));
        return auditLogRepository.findAll(spec,
                PageRequest.of(0, size, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent();
    }
}
