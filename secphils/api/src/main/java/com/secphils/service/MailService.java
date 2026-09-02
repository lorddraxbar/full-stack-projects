package com.secphils.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Transactional mail via the configured SMTP provider (Zoho).
 * Mail failures are logged, never thrown — a broken SMTP relay must not
 * block user creation or other API calls.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final EmailTemplateService templateService;

    public MailService(JavaMailSender mailSender,
                       @Value("${spring.mail.from}") String fromAddress,
                       EmailTemplateService templateService) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.templateService = templateService;
    }

    public void sendHtml(String to, String subject, String htmlBody, String link) {
        sendHtml(to, subject, htmlBody, link, null);
    }

    /**
     * HTML mail with an optional Reply-To, so recipients can reply straight to
     * the sender (e.g. a website visitor). {@code replyTo} null = no Reply-To.
     */
    public void sendHtml(String to, String subject, String htmlBody, String link, String replyTo) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            if (replyTo != null && !replyTo.isBlank()) {
                helper.setReplyTo(replyTo);
            }
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Mail sent to {} — {} (link: {})", to, subject, link);
        } catch (Exception e) {
            log.error("Failed to send mail to {} — {}: {}", to, subject, e.getMessage(), e);
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
        Map<String, String> vars = Map.of(
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
