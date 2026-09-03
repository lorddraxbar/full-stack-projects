package com.secphils.common;

import com.secphils.entity.AuditLog;
import com.secphils.entity.Notification;
import com.secphils.entity.User;
import com.secphils.repository.AuditLogRepository;
import com.secphils.repository.NotificationRepository;
import com.secphils.security.AuthUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
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
import java.util.Comparator;
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
    private final EntityManager em;

    public AuditService(AuditLogRepository auditLogRepository, NotificationRepository notificationRepository,
                        EntityManager em) {
        this.auditLogRepository = auditLogRepository;
        this.notificationRepository = notificationRepository;
        this.em = em;
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

    /** Filtered, newest-first audit log query for the admin console.
     *  `search` is a substring match over action, entity type, user name, and
     *  the details payload (details is JSONB — cast to text; a plain
     *  comparison errors with "invalid input syntax for type json"). */
    @Transactional(readOnly = true)
    public List<AuditLog> query(String action, Long userId, int limit, String search) {
        StringBuilder jpql = new StringBuilder(
                "from AuditLog a where 1=1");
        java.util.Map<String, Object> args = new java.util.LinkedHashMap<>();
        if (action != null && !action.isBlank()) {
            jpql.append(" and a.action = :action");
            args.put("action", action);
        }
        if (userId != null) {
            jpql.append(" and a.user.id = :userId");
            args.put("userId", userId);
        }
        if (search != null && !search.isBlank()) {
            jpql.append(" and (lower(a.action) like :q or lower(a.entityType) like :q"
                    + " or lower(a.user.firstName) like :q or lower(a.user.lastName) like :q"
                    + " or lower(cast(a.details as string)) like :q)");
            args.put("q", "%" + search.trim().toLowerCase() + "%");
        }
        int size = Math.max(1, Math.min(limit, 500));
        TypedQuery<AuditLog> q = em.createQuery(jpql.toString(), AuditLog.class)
                .setMaxResults(size);
        for (var entry : args.entrySet()) q.setParameter(entry.getKey(), entry.getValue());
        return q.getResultList().stream()
                .sorted(Comparator.comparing(AuditLog::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }
}
