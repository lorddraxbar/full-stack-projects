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
 * Writers: the SES/SNS webhook only — hard bounces and spam complaints add
 * address-wide rows ({@code category=""}), which stop ALL mail to the
 * address, even mandatory emails (a dead mailbox is a dead mailbox).
 * Unsubscribes are NOT stored here: they live in
 * {@code notification_preferences.email} so the recipient can flip them back
 * on from their own preferences page (a ledger row would silently override
 * those toggles — dead-switch theater). Category-granular rows remain
 * representable ({@code category} = a notification key) for future writers;
 * none exist today.
 */
@Service
public class EmailSuppressionService {

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

    /** Newest first — backs the Admin → System suppressions panel. */
    public List<EmailSuppression> listAll() {
        return suppressions.findAll(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
    }

    @Transactional
    public boolean deleteById(Long id) {
        if (!suppressions.existsById(id)) return false;
        suppressions.deleteById(id);
        return true;
    }
}
