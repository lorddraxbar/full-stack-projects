package com.secphils.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secphils.entity.SystemSettings;
import com.secphils.repository.SystemSettingsRepository;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

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
    private final SystemSettingsRepository settingsRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MailService(JavaMailSender mailSender,
                       @Value("${spring.mail.from}") String fromAddress,
                       SystemSettingsRepository settingsRepository) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.settingsRepository = settingsRepository;
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
     * Invite email. Prefers the admin-editable "Team Invitation" template from
     * system_settings.email_templates (subject + body with {{placeholders}});
     * falls back to the built-in branded HTML if the template is absent,
     * malformed, or empty.
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
        JsonNode template = findInviteTemplate();
        if (template != null) {
            String body = template.path("body").asText("");
            if (!body.isBlank()) {
                String rendered = body
                        .replace("{{name}}", firstNonBlank(firstName, fullName))
                        .replace("{{fullName}}", firstNonBlank(fullName, firstName))
                        .replace("{{inviter}}", inviter != null ? inviter : "A member")
                        .replace("{{company}}", company != null ? company : "the SECPhils Portal")
                        .replace("{{setupLink}}", link);
                // Simple text -> HTML so it reads well in an HTML mailbox.
                return "<!DOCTYPE html><html><body style=\"margin:0;padding:0;background:#f4f5f7;"
                        + "font-family:Arial,Helvetica,sans-serif;color:#1f2937;\">"
                        + "<div style=\"max-width:560px;margin:32px auto;padding:32px;background:#ffffff;"
                        + "border-radius:12px;border:1px solid #e5e7eb;\">"
                        + "<p style=\"font-size:14px;line-height:1.6;\">"
                        + rendered.replace("\n", "<br>")
                        + "</p></div></body></html>";
            }
        }
        return defaultInviteEmail(firstName, fullName, link);
    }

    /** Locate the admin-editable invite template in system_settings.email_templates. */
    private JsonNode findInviteTemplate() {
        try {
            SystemSettings settings = settingsRepository.findAll().stream().findFirst().orElse(null);
            if (settings == null || settings.getEmailTemplates() == null || settings.getEmailTemplates().isBlank()) {
                return null;
            }
            JsonNode root = objectMapper.readTree(settings.getEmailTemplates());
            if (root.isArray()) {
                for (JsonNode node : root) {
                    String name = node.path("name").asText("");
                    if (name.equalsIgnoreCase("Team Invitation") || name.equalsIgnoreCase("Invite")) {
                        return node;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Could not read email_templates; using default invite email: {}", e.getMessage());
        }
        return null;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : (b != null ? b : "");
    }

    private String defaultInviteEmail(String firstName, String fullName, String link) {
        return """
                <!DOCTYPE html>
                <html>
                <body style="margin:0;padding:0;background:#f4f5f7;font-family:Arial,Helvetica,sans-serif;color:#1f2937;">
                  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#f4f5f7;padding:32px 0;">
                    <tr><td align="center">
                      <table role="presentation" width="560" cellpadding="0" cellspacing="0"
                             style="background:#ffffff;border-radius:12px;overflow:hidden;border:1px solid #e5e7eb;">
                        <tr><td style="background:#1d4ed8;padding:24px 32px;">
                          <span style="color:#ffffff;font-size:20px;font-weight:bold;">SECPhils</span>
                        </td></tr>
                        <tr><td style="padding:32px;">
                          <h1 style="margin:0 0 16px;font-size:18px;font-weight:600;">Welcome, %FIRSTNAME%</h1>
                          <p style="margin:0 0 16px;font-size:14px;line-height:1.6;">
                            You have been invited to join the SECPhils portal.
                            Set your password to activate it — no one else, including us, knows it.
                          </p>
                          <table role="presentation" cellpadding="0" cellspacing="0" style="margin:24px 0;">
                            <tr><td style="background:#1d4ed8;border-radius:8px;">
                              <a href="%LINK%"
                                 style="display:inline-block;padding:12px 28px;font-size:14px;font-weight:600;
                                        color:#ffffff;text-decoration:none;border-radius:8px;">
                                Set my password
                              </a>
                            </td></tr>
                          </table>
                          <p style="margin:0 0 8px;font-size:13px;line-height:1.6;color:#6b7280;">
                            Or paste this link into your browser:
                          </p>
                          <p style="margin:0 0 24px;font-size:13px;word-break:break-all;color:#6b7280;">%LINK%</p>
                          <p style="margin:0;font-size:12px;color:#9ca3af;line-height:1.5;">
                            This link expires in 24 hours. If you were not expecting this email, you can safely ignore it.
                          </p>
                        </td></tr>
                        <tr><td style="padding:16px 32px;background:#f9fafb;border-top:1px solid #e5e7eb;">
                          <p style="margin:0;font-size:12px;color:#9ca3af;">SECPhils Portal — notifications@secphils.com</p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.replace("%FIRSTNAME%", firstName)
                   .replace("%FULLNAME%", fullName)
                   .replace("%LINK%", link);
    }
}
