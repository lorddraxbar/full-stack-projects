package com.secphils.service;

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

    public MailService(JavaMailSender mailSender, @Value("${spring.mail.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void sendHtml(String to, String subject, String htmlBody, String link) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Mail sent to {} — {} (link: {})", to, subject, link);
        } catch (Exception e) {
            log.error("Failed to send mail to {} — {}: {}", to, subject, e.getMessage(), e);
        }
    }

    public String inviteEmail(String firstName, String fullName, String link) {
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
