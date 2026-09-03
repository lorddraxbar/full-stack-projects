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

    /**
     * Shared WHERE builder for the audit-log query and its row count, so both
     * always apply the same filter (action, user, substring search). The
     * search matches action, entity type, user first/last name, and the
     * details payload (details is JSONB — cast to text; a plain comparison
     * errors with "invalid input syntax for type json"). Leading space so it
     * can be prepended to a "select count(a)" for the total.
     */
    private String baseWhere(java.util.Map<String, Object> args,
                             String action, Long userId, String search) {
        // LEFT JOIN the author: audit rows with a NULL user (e.g. login events
        // recorded before a session exists — 1,087 of ~1,881 rows) must NOT be
        // dropped. Referencing a.user.firstName directly would make Hibernate
        // emit an INNER join and silently filter those rows out (search "login"
        // returned 0 even though the DB has 1,029 USER_LOGIN rows).
        StringBuilder jpql = new StringBuilder(" from AuditLog a left join a.user u where 1=1");
        if (action != null && !action.isBlank()) {
            jpql.append(" and a.action = :action");
            args.put("action", action);
        }
        if (userId != null) {
            jpql.append(" and u.id = :userId");
            args.put("userId", userId);
        }
        if (search != null && !search.isBlank()) {
            jpql.append(" and (lower(a.action) like :q or lower(a.entityType) like :q"
                    + " or lower(u.firstName) like :q or lower(u.lastName) like :q"
                    + " or lower(cast(a.details as string)) like :q)");
            args.put("q", "%" + search.trim().toLowerCase() + "%");
        }
        return jpql.toString();
    }

    /**
     * Total audit logs matching the filter — the "of Z" in the admin
     * pagination footer (shows the real count, e.g. 1,881, not the page cap).
     */
    @Transactional(readOnly = true)
    public long count(String action, Long userId, String search) {
        java.util.Map<String, Object> args = new java.util.LinkedHashMap<>();
        TypedQuery<Long> q = em.createQuery(
                "select count(a)" + baseWhere(args, action, userId, search), Long.class);
        for (var entry : args.entrySet()) q.setParameter(entry.getKey(), entry.getValue());
        return q.getSingleResult();
    }

    /**
     * Filtered, newest-first, SERVER-SIDE-paginated audit log query for the
     * admin console. `page` is 0-based, `size` clamped to [1, 500]. The ORDER
     * BY is in the query (not a client sort) so paging is stable and the DB
     * does the work.
     */
    @Transactional(readOnly = true)
    public List<AuditLog> query(String action, Long userId, int page, int size, String search) {
        java.util.Map<String, Object> args = new java.util.LinkedHashMap<>();
        String jpql = baseWhere(args, action, userId, search)
                + " order by a.createdAt desc, a.id desc";
        int safeSize = Math.max(1, Math.min(size, 500));
        int safePage = Math.max(0, page);
        TypedQuery<AuditLog> q = em.createQuery(jpql, AuditLog.class)
                .setFirstResult(safePage * safeSize)
                .setMaxResults(safeSize);
        for (var entry : args.entrySet()) q.setParameter(entry.getKey(), entry.getValue());
        return q.getResultList();
    }
}
