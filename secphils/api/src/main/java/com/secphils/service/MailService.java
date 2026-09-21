package com.secphils.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secphils.dto.SmtpConfig;
import com.secphils.security.AuthUser;
import com.secphils.entity.SystemSettings;
import com.secphils.entity.User;
import com.secphils.repository.SystemSettingsRepository;
import com.secphils.repository.UserRepository;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Properties;

/**
 * Transactional mail. The effective relay comes from
 * {@code system_settings.smtp} (Admin Settings → SMTP, V34) and is read live
 * on every send — saving new credentials takes effect immediately, no restart.
 * With no usable DB config it falls back to the env-configured sender
 * (spring.mail.*), so a fresh deploy works from .env before anyone opens the
 * admin panel. Mail failures are logged, never thrown — a broken SMTP relay
 * must not block user creation or other API calls.
 *
 * <p>Email-preference plumbing (V40), applied in {@link #sendHtml} so EVERY
 * notification path gets it from one place:
 * <ul>
 *   <li><b>Suppression gate</b> — addresses in {@code email_suppressions}
 *       (hard bounce / complaint / whole-address unsubscribe) never receive
 *       another email, and a category suppression drops that one category.
 *       Applies even to the mandatory emails: a hard-bounced mailbox is a
 *       hard stop for everything.</li>
 *   <li><b>List-Unsubscribe (RFC 8058)</b> — emails sent with a notification
 *       {@code category} carry the one-click headers pointing at
 *       /api/v1/email/unsubscribe; invites, security notices, and landing
 *       messages send WITHOUT the header (they are transactional, and a
 *       one-click button on them would be a lie).</li>
 * </ul>
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSenderImpl envSender;
    private final String envFrom;
    private final SystemSettingsRepository settingsRepository;
    private final EmailTemplateService templateService;
    private final EmailSuppressionService suppressions;
    private final UserRepository userRepository;
    private final NotificationPrefs prefs;
    private final com.secphils.common.AuditService auditService;
    private final Duration inviteTtl;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.invite.base-url}")
    private String envBaseUrl;

    public MailService(org.springframework.mail.javamail.JavaMailSender mailSender,
                       @Value("${spring.mail.from}") String fromAddress,
                       SystemSettingsRepository settingsRepository,
                       EmailTemplateService templateService,
                       EmailSuppressionService suppressions,
                       UserRepository userRepository,
                       NotificationPrefs prefs,
                       com.secphils.common.AuditService auditService,
                       @Value("${app.invite.token-ttl:24h}") Duration inviteTtl) {
        this.envSender = (JavaMailSenderImpl) mailSender;
        this.envFrom = fromAddress;
        this.settingsRepository = settingsRepository;
        this.templateService = templateService;
        this.suppressions = suppressions;
        this.userRepository = userRepository;
        this.prefs = prefs;
        this.auditService = auditService;
        this.inviteTtl = inviteTtl;
    }

    /** The DB config if usable, else null (caller falls back to env). A
     *  password equal to the read-mask is treated as UNUSABLE — the mask must
     *  never reach the wire (footgun if the settings row ever holds it). */
    private SmtpConfig dbConfig() {
        SystemSettings s = settingsRepository.findAll().stream().findFirst().orElse(null);
        if (s == null || s.getSmtp() == null || s.getSmtp().isBlank()) return null;
        try {
            SmtpConfig cfg = objectMapper.readValue(s.getSmtp(), SmtpConfig.class);
            if (SmtpConfig.SECRET_MASK.equals(cfg.password)) {
                log.warn("system_settings.smtp holds the read-mask as password — ignoring DB config, using env SMTP");
                return null;
            }
            return cfg.isConfigured() ? cfg : null;
        } catch (Exception e) {
            log.warn("Unreadable smtp settings JSON — falling back to env SMTP: {}", e.getMessage());
            return null;
        }
    }

    /** Resolve a sender for the given config (DB wins, env is the default). */
    private JavaMailSenderImpl senderFor(SmtpConfig cfg) {
        if (cfg == null) return envSender;
        JavaMailSenderImpl s = new JavaMailSenderImpl();
        s.setHost(cfg.host);
        s.setPort(cfg.port);
        s.setUsername(cfg.username);
        s.setPassword(cfg.password);
        s.setDefaultEncoding("UTF-8");
        boolean ssl = cfg.port == 465;
        Properties p = s.getJavaMailProperties();
        p.put("mail.smtp.auth", "true");
        p.put("mail.smtp.ssl.enable", String.valueOf(ssl));
        p.put("mail.smtp.ssl.trust", cfg.host);
        if (!ssl) p.put("mail.smtp.starttls.enable", "true");
        p.put("mail.smtp.connectiontimeout", "10000");
        p.put("mail.smtp.timeout", "10000");
        p.put("mail.smtp.writetimeout", "10000");
        return s;
    }

    private String fromFor(SmtpConfig cfg) {
        if (cfg != null && cfg.from != null && !cfg.from.isBlank()) return cfg.from;
        return envFrom;
    }

    public void sendHtml(String to, String subject, String htmlBody, String link) {
        sendHtml(to, subject, htmlBody, link, null, null, null);
    }

    public void sendHtml(String to, String subject, String htmlBody, String link, String replyTo) {
        sendHtml(to, subject, htmlBody, link, replyTo, null, null);
    }

    /**
     * Async delivery inside its OWN transaction: the SMTP send can run for
     * seconds after the caller's response is written, and without a
     * transaction of its own its lazy loads (recipient preferences,
     * unsubscribe token mint) hit a closed session when it runs after an
     * HTTP request's OSIV window. REQUIRES_NEW keeps it independent of
     * whatever scope it lands in.
     */
    @Async("mailExecutor")
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void sendHtmlAsync(String to, String subject, String htmlBody, String link,
                              String replyTo, String category, User recipient) {
        sendHtml(to, subject, htmlBody, link, replyTo, category, recipient);
    }

    /** 4-arg async door, mirroring the sync overload (no Reply-To/category). */
    @Async("mailExecutor")
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void sendHtmlAsync(String to, String subject, String htmlBody, String link) {
        sendHtml(to, subject, htmlBody, link, null, null, null);
    }

    /**
     * Portal rule: an obligation email (review/approve, rep assignment) is
     * only honest when its recipient can ACT on it. An authorized rep may be
     * a fully registered user or merely a staff-created placeholder row that
     * never became an account — no password AND never signed in. Such a rep
     * hitting a portal link bounces off a sign-in they cannot pass (exactly
     * Jaybar's 2026-09-21 repro). True = can sign in (password set, or has
     * signed in via SSO at least once).
     */
    public boolean hasPortalAccount(User rep) {
        return rep != null && (rep.getPasswordHash() != null || rep.getLastLogin() != null);
    }

    /**
     * Rep-account gate: when a notification wants to send an obligation
     * email to a rep WITHOUT an account, send them the branded SET-PASSWORD
     * invitation instead (same token machinery + audit trail as the team
     * invite path). Id-based on purpose: safe to call from the mail threads
     * — the rep row is re-read fresh, never a cross-thread entity reference.
     *
     * Runs on the mail executor in its own transaction; re-checks the
     * account state after loading (someone may have set a password between
     * the caller's check and our send). A pending-but-live invite token is
     * honored: no second spam invite while one waits — the earlier link and
     * the newer one lead to the same page anyway. Never throws: mail
     * transport failures are logged by sendHtml, and a failed invite must
     * not undo the caller's save.
     */
    @Async("mailExecutor")
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void ensureAccountInvited(Long actorId, Long repId, String companyName, String auditAction) {
        try {
            User rep = userRepository.findById(repId).orElse(null);
            if (rep == null || hasPortalAccount(rep)) return;
            boolean pendingInvite = rep.getPasswordResetToken() != null
                    && rep.getPasswordResetExpiresAt() != null
                    && rep.getPasswordResetExpiresAt().isAfter(java.time.LocalDateTime.now());
            if (pendingInvite) {
                log.info("Rep {} already has a live invite — no duplicate sent ({})", repId, auditAction);
                return;
            }
            String token = inviteToken();
            rep.setPasswordResetToken(token);
            rep.setPasswordResetExpiresAt(java.time.LocalDateTime.now().plus(inviteTtl));
            rep.setPasswordResetRequestedAt(java.time.LocalDateTime.now());
            userRepository.save(rep);
            String link = resolveBaseUrl(null) + "/auth/set-password?token=" + token;
            String inviter = actorId == null ? "A member"
                    : userRepository.findById(actorId)
                            .map(com.secphils.policy.DisplayNamePolicy::nameFor).orElse("A member");
            sendHtml(rep.getEmail(), inviteSubject(companyName),
                    inviteEmail(rep.getFirstName(), rep.getFullName(), link, inviter, companyName), link);
            AuthUser actor = actorId == null ? null : userRepository.findById(actorId)
                    .map(u -> new AuthUser(u.getId(), u.getEmail(), u.getRole(), u.getCompanyId())).orElse(null);
            auditService.audit(actor, auditAction, "User", repId,
                    "Auto-invite (rep has no portal account) — email: " + rep.getEmail(), null);
        } catch (Exception e) {
            log.warn("ensureAccountInvited(rep={}) failed: {}", repId, e.getMessage());
        }
    }

    /**
     * HTML mail with an optional Reply-To and optional notification category.
     * {@code replyTo} null = no Reply-To. {@code category} is the notification
     * preference key (e.g. {@code newMessage}); null marks a mandatory/
     * transactional email — no unsubscribe header, only the address-wide
     * suppression gate applies. {@code recipient} may be null (resolved from
     * {@code to}) — pass it when the caller already has the User to save a
     * lookup.
     */
    public void sendHtml(String to, String subject, String htmlBody, String link,
                         String replyTo, String category, User recipient) {
        SmtpConfig cfg = dbConfig();
        try {
            User user = recipient != null ? recipient
                    : userRepository.findByEmailIgnoreCase(to == null ? "" : to.trim()).orElse(null);
            if (suppressions.isSuppressed(to, category)) {
                log.info("Mail to {} skipped — suppressed (category: {})", to, category);
                return;
            }
            JavaMailSenderImpl sender = senderFor(cfg);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromFor(cfg));
            helper.setTo(to);
            helper.setSubject(subject);
            if (replyTo != null && !replyTo.isBlank()) {
                helper.setReplyTo(replyTo);
            }
            if (category != null && !category.isBlank() && user != null) {
                String url = unsubscribeLink(user);
                message.setHeader("List-Unsubscribe", "<" + url + ">");
                message.setHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
                // Visible, in-body unsubscribe line too (webmail header buttons
                // are easy to miss; content reviewers expect a plain link).
                htmlBody = htmlBody + "<p style=\"margin:16px 0 0;font-size:12px;color:#9ca3af;\">"
                        + "These emails are notifications for the SECPhils projects you take part in. "
                        + "<a href=\"" + url + "\" style=\"color:#9ca3af;\">Manage email preferences</a>"
                        + "</p>";
            }
            helper.setText(htmlBody, true);
            sender.send(message);
            log.info("Mail sent to {} — {} (link: {})", to, subject, link);
        } catch (Exception e) {
            log.error("Failed to send mail to {} — {}: {}", to, subject, e.getMessage());
        }
    }

    /**
     * Portal base URL for email links: admin setting first, then the request's
     * Origin/Referer (fresh deploys work unconfigured), then env. Package-wide
     * convention also used by invite links (UserController/CompanyController).
     */
    public String resolveBaseUrl(HttpServletRequest http) {
        String fromSettings = settingsRepository.findAll().stream().findFirst()
                .map(SystemSettings::getInviteBaseUrl)
                .filter(s -> s != null && !s.isBlank())
                .orElse(null);
        if (fromSettings != null) return fromSettings.replaceAll("/+$", "");
        if (http != null) {
            String origin = http.getHeader("Origin");
            if (origin == null || origin.isBlank()) {
                String referer = http.getHeader("Referer");
                if (referer != null && !referer.isBlank()) {
                    try {
                        java.net.URI u = java.net.URI.create(referer);
                        if (u.getScheme() != null && u.getAuthority() != null) {
                            origin = u.getScheme() + "://" + u.getAuthority();
                        }
                    } catch (Exception ignored) {
                        // malformed referer — fall through
                    }
                }
            }
            if (origin != null && !origin.isBlank()) return origin.replaceAll("/+$", "");
        }
        return envBaseUrl.replaceAll("/+$", "");
    }

    /** One-time invite/set-password token (32 random bytes, hex) — same
     *  shape the controller invite paths mint, shared here for the gate. */
    private String inviteToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    /** Stable per-user unsubscribe token (V40), minted on first use. */
    public String tokenFor(User u) {
        if (u.getUnsubscribeToken() != null && !u.getUnsubscribeToken().isBlank()) {
            return u.getUnsubscribeToken();
        }
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        u.setUnsubscribeToken(sb.toString());
        userRepository.save(u);
        return u.getUnsubscribeToken();
    }

    /** The tokenized preferences-link a notification email should carry. */
    public String unsubscribeLink(User u) {
        return unsubscribeLink(resolveBaseUrl(null), u);
    }

    public String unsubscribeLink(HttpServletRequest http, User u) {
        return unsubscribeLink(resolveBaseUrl(http), u);
    }

    private String unsubscribeLink(String base, User u) {
        return base + "/api/v1/email/preferences?t=" + tokenFor(u);
    }

    /**
     * Live connectivity probe for Admin Settings → SMTP: attempts one real
     * send to {@code to} using the PROVIDED config (not the stored one), so
     * admins can validate before saving. Throws nothing — returns the
     * {ok, message} verdict.
     */
    public java.util.Map<String, Object> testSend(SmtpConfig cfg, String to) {
        if (!cfg.isConfigured()) {
            return java.util.Map.of("ok", false, "message", "Host and username are required.");
        }
        try {
            JavaMailSenderImpl sender = senderFor(cfg);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromFor(cfg));
            helper.setTo(to);
            helper.setSubject("SECPhils SMTP test");
            helper.setText("This is a live delivery test from SECPhils Admin Settings. If you received it, the relay works.", true);
            sender.send(message);
            return java.util.Map.of("ok", true,
                    "message", "Test email sent to " + to + " via " + cfg.host + ":" + cfg.port);
        } catch (Exception e) {
            return java.util.Map.of("ok", false,
                    "message", "SMTP failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    /**
     * Invite email subject. Prefers the admin-editable "invite" template
     * subject; the default is the historical "Your SECPhils Portal access is
     * ready" line. {@code company} fills {{company}} — the subject is NOT
     * variable-free once an admin-stored template carries the placeholder,
     * so the company must be plumbed here (blank/null falls back to the
     * portal wording).
     */
    public String inviteSubject(String company) {
        return templateService.subject(EmailTemplateService.INVITE,
                java.util.Map.of("company", company != null && !company.isBlank()
                        ? company : "the SECPhils Portal"));
    }

    /**
     * Invite email. Renders the admin-editable "invite" template
     * (subject/kicker/heading/body/CTA/footer from the Email Templates
     * settings; blanks fall back to the built-in defaults) into the shared
     * branded card, with the setup link as the CTA target.
     */
    public String inviteEmail(String firstName, String fullName, String link) {
        return renderInvite(firstName, fullName, link, null, null);
    }

    /**
     * Invite email with an optional inviter and company name, so the template
     * can fill {{inviter}} and {{company}}.
     */
    public String inviteEmail(String firstName, String fullName, String link, String inviter, String company) {
        return renderInvite(firstName, fullName, link, inviter, company);
    }

    private String renderInvite(String firstName, String fullName, String link, String inviter, String company) {
        java.util.Map<String, String> vars = java.util.Map.of(
                "name", firstNonBlank(firstName, fullName),
                "fullName", firstNonBlank(fullName, firstName),
                "inviter", inviter != null ? inviter : "A member",
                "company", company != null ? company : "the SECPhils Portal",
                "setupLink", link);
        return templateService.brandedCard(
                templateService.kicker(EmailTemplateService.INVITE, vars),
                templateService.heading(EmailTemplateService.INVITE, vars),
                templateService.bodyHtml(EmailTemplateService.INVITE, vars),
                templateService.cta(EmailTemplateService.INVITE, vars),
                link,
                templateService.footer(EmailTemplateService.INVITE, vars));
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b != null ? b : "");
    }
}
