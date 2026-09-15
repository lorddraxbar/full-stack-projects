package com.secphils.service;

import com.secphils.entity.EmailSuppression;
import com.secphils.entity.User;
import com.secphils.repository.EmailSuppressionRepository;
import com.secphils.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * The suppression ledger behind {@code MailService}'s pre-send gate (V40).
 * Two granularities, per V40: {@code category=""} suppresses the whole
 * address (hard bounce / spam complaint / bulk unsubscribe), a notification
 * key suppresses one category (one-click List-Unsubscribe). Matching is
 * case-insensitive on the address.
 */
@Service
public class EmailSuppressionService {

    public static final String REASON_UNSUBSCRIBE = "unsubscribe";
    public static final String REASON_BOUNCE = "bounce";
    public static final String REASON_COMPLAINT = "complaint";

    private final EmailSuppressionRepository suppressions;
    private final UserRepository users;

    public EmailSuppressionService(EmailSuppressionRepository suppressions, UserRepository users) {
        this.suppressions = suppressions;
        this.users = users;
    }

    public boolean isSuppressed(String email, String category) {
        if (email == null || email.isBlank()) return false;
        String e = email.trim();
        return suppressions.findByEmailIgnoreCaseAndCategory(e, "").isPresent()
                || (category != null && !category.isBlank()
                    && suppressions.findByEmailIgnoreCaseAndCategory(e, category).isPresent());
    }

    /** Idempotent insert (unique key email+category). */
    @Transactional
    public void add(String email, String category, String reason, String detail, Long userId) {
        String e = email == null ? "" : email.trim();
        if (e.isEmpty()) return;
        String cat = category == null ? "" : category.trim();
        if (suppressions.findByEmailIgnoreCaseAndCategory(e, cat).isPresent()) return;
        EmailSuppression s = new EmailSuppression();
        s.setEmail(e);
        s.setCategory(cat);
        s.setReason(reason);
        s.setDetail(detail == null ? null : (detail.length() > 500 ? detail.substring(0, 500) : detail));
        s.setUserId(userId != null ? userId : users.findByEmailIgnoreCase(e).map(User::getId).orElse(null));
        suppressions.save(s);
    }

    @Transactional
    public void remove(String email, String category) {
        suppressions.findByEmailIgnoreCaseAndCategory(email.trim(), category == null ? "" : category.trim())
                .ifPresent(suppressions::delete);
    }

    public List<EmailSuppression> forAddress(String email) {
        return suppressions.findByEmailIgnoreCase(email == null ? "" : email.trim());
    }
}
