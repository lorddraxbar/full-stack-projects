package com.secphils.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secphils.dto.SmtpConfig;
import com.secphils.entity.SystemSettings;
import com.secphils.repository.SystemSettingsRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Properties;

/**
 * Transactional mail. The effective relay comes from
 * {@code system_settings.smtp} (Admin Settings → SMTP, V34) and is read live
 * on every send — saving new credentials takes effect immediately, no restart.
 * With no usable DB config it falls back to the env-configured sender
 * (spring.mail.*), so a fresh deploy works from .env before anyone opens the
 * admin panel. Mail failures are logged, never thrown — a broken SMTP relay
 * must not block user creation or other API calls.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSenderImpl envSender;
    private final String envFrom;
    private final SystemSettingsRepository settingsRepository;
    private final EmailTemplateService templateService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MailService(org.springframework.mail.javamail.JavaMailSender mailSender,
                       @Value("${spring.mail.from}") String fromAddress,
                       SystemSettingsRepository settingsRepository,
                       EmailTemplateService templateService) {
        this.envSender = (JavaMailSenderImpl) mailSender;
        this.envFrom = fromAddress;
        this.settingsRepository = settingsRepository;
        this.templateService = templateService;
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
        sendHtml(to, subject, htmlBody, link, null);
    }

    /**
     * HTML mail with an optional Reply-To, so recipients can reply straight to
     * the sender (e.g. a website visitor). {@code replyTo} null = no Reply-To.
     */
    public void sendHtml(String to, String subject, String htmlBody, String link, String replyTo) {
        SmtpConfig cfg = dbConfig();
        try {
            JavaMailSenderImpl sender = senderFor(cfg);
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromFor(cfg));
            helper.setTo(to);
            helper.setSubject(subject);
            if (replyTo != null && !replyTo.isBlank()) {
                helper.setReplyTo(replyTo);
            }
            helper.setText(htmlBody, true);
            sender.send(message);
            log.info("Mail sent to {} — {} (link: {})", to, subject, link);
        } catch (Exception e) {
            log.error("Failed to send mail to {} — {}: {}", to, subject, e.getMessage());
        }
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
     * ready" line (the template has no variables, so it's resolved once here).
     */
    public String inviteSubject() {
        return templateService.subject(EmailTemplateService.INVITE, java.util.Map.of());
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
