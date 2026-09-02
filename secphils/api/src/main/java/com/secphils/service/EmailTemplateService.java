package com.secphils.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secphils.entity.SystemSettings;
import com.secphils.repository.SystemSettingsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Admin-editable email templates, shared by every transactional email in the
 * app. The catalog lives in {@code system_settings.email_templates} (a JSON
 * array managed in Admin Settings → Email Templates); this service resolves a
 * template by name, merges it over the built-in defaults (any field the admin
 * left blank keeps its default), and renders the {{placeholders}}.
 *
 * <p>Template fields, in order: {@code subject} (email subject line),
 * {@code kicker} (small teal line above the headline in the card),
 * {@code heading} (the card's <h1>), {@code body} (plain text — line breaks
 * become <br>, {@code **bold**} becomes <strong>, everything is HTML-escaped),
 * {@code cta} (button label; the link target is supplied by the sender), and
 * {@code footer} (the small grey note at the bottom of the card).
 *
 * <p>The landing-page contact email is special: its {@code body} is full HTML
 * (a standalone card), rendered by {@link #landingContactHtml(Map)}.
 *
 * <p>Known template names: {@code internalMessage}, {@code clientMessage},
 * {@code announcement}, {@code invite}, {@code projectCreatedRep},
 * {@code projectCreatedStaff}, {@code projectStatusRep},
 * {@code projectStatusStaff}, {@code projectArchived}, {@code projectRestored},
 * {@code landing}.
 *
 * <p>Mail can never fail a request: if the stored JSON is unreadable the
 * built-in defaults are used (same convention as the original invite-template
 * lookup this class replaces).
 */
@Service
public class EmailTemplateService {

    private static final Logger log = LoggerFactory.getLogger(EmailTemplateService.class);

    public static final String INTERNAL_MESSAGE = "internalMessage";
    public static final String CLIENT_MESSAGE = "clientMessage";
    public static final String ANNOUNCEMENT = "announcement";
    public static final String INVITE = "invite";
    public static final String PROJECT_CREATED_REP = "projectCreatedRep";
    public static final String PROJECT_CREATED_STAFF = "projectCreatedStaff";
    public static final String PROJECT_STATUS_REP = "projectStatusRep";
    public static final String PROJECT_STATUS_STAFF = "projectStatusStaff";
    public static final String PROJECT_ARCHIVED = "projectArchived";
    public static final String PROJECT_RESTORED = "projectRestored";
    public static final String LANDING = "landing";

    private final SystemSettingsRepository settingsRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EmailTemplateService(SystemSettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    /** One editable email template. All fields optional; blanks fall back to the default. */
    public record EmailTemplate(String name, String subject, String kicker, String heading,
                                String body, String cta, String footer) {
        public static EmailTemplate of(String name, String subject, String kicker, String heading,
                                       String body, String cta, String footer) {
            return new EmailTemplate(name, subject, kicker, heading, body, cta, footer);
        }
    }

    /**
     * Built-in defaults — a faithful copy of the pre-configurable hardcoded
     * emails (subjects, kickers, headings, bodies, CTAs, footers). An admin
     * who has never opened Email Settings sees exactly what shipped.
     */
    public static final Map<String, EmailTemplate> DEFAULTS = Map.ofEntries(
            Map.entry(INTERNAL_MESSAGE, EmailTemplate.of(INTERNAL_MESSAGE,
                    "Internal message from {{sender}} · {{project}}",
                    "SecPhils · Internal · {{project}}",
                    "Internal message from {{sender}}",
                    "{{body}}",
                    "Open the conversation",
                    "You're receiving this as a provider team member. Internal messages are not visible to the client. Manage your notification preferences in the portal.")),
            Map.entry(CLIENT_MESSAGE, EmailTemplate.of(CLIENT_MESSAGE,
                    "New message from {{sender}} · {{project}}",
                    "SecPhils · {{project}}",
                    "New message from {{sender}}",
                    "{{body}}",
                    "Open the conversation",
                    "You're receiving this as a member of the project's company. Manage your notification preferences in the portal.")),
            Map.entry(ANNOUNCEMENT, EmailTemplate.of(ANNOUNCEMENT,
                    "SecPhils — {{title}}",
                    "SecPhils · {{category}}",
                    "{{title}}{{projectRef}}",
                    "{{body}}",
                    "View all announcements",
                    "You're receiving this as a member of {{company}}. Manage your notification preferences in the portal.")),
            Map.entry(INVITE, EmailTemplate.of(INVITE,
                    "Your SECPhils Portal access is ready",
                    "SecPhils",
                    "Welcome, {{name}}",
                    "Hi {{name}},\n\n{{inviter}} invited you to the SECPhils portal.\n\nSet your password via the button below to activate your account — no one else, including us, knows it.",
                    "Set my password",
                    "This link expires in 24 hours. If you were not expecting this email, you can safely ignore it.")),
            Map.entry(PROJECT_CREATED_REP, EmailTemplate.of(PROJECT_CREATED_REP,
                    "New project submitted for {{company}}",
                    "SecPhils",
                    "New project submitted — {{project}}",
                    "Hi {{name}},\n\n{{company}} just submitted the project \"{{project}}\" for review. Please open it in the portal, check the details, and mark it complete when everything looks right.",
                    "Open {{project}}",
                    "You're receiving this as the authorized representative of the customer company. Manage your notification preferences in the portal.")),
            Map.entry(PROJECT_CREATED_STAFF, EmailTemplate.of(PROJECT_CREATED_STAFF,
                    "New project for {{company}} — {{project}}",
                    "SecPhils",
                    "New project — {{project}}",
                    "Hi {{name}},\n\n{{company}} submitted a new project, \"{{project}}\"{{repNote}}. It is waiting for review and completion.",
                    "View the project",
                    "You're receiving this as a member of the SECPhils provider team. Manage your notification preferences in the portal.")),
            Map.entry(PROJECT_STATUS_REP, EmailTemplate.of(PROJECT_STATUS_REP,
                    "{{project}} is now {{statusLabel}}",
                    "SecPhils",
                    "Project {{statusLabel}} — {{project}}",
                    "Hi {{name}},\n\nThe project \"{{project}}\" ({{company}}) is now **{{statusLabel}}**.",
                    "View the project",
                    "You're receiving this as the authorized representative of the customer company. Manage your notification preferences in the portal.")),
            Map.entry(PROJECT_STATUS_STAFF, EmailTemplate.of(PROJECT_STATUS_STAFF,
                    "{{project}} is now {{statusLabel}}",
                    "SecPhils",
                    "Project {{statusLabel}} — {{project}}",
                    "Hi {{name}},\n\n{{company}}'s project \"{{project}}\" is now **{{statusLabel}}**.",
                    "View the project",
                    "You're receiving this as a member of the SECPhils provider team. Manage your notification preferences in the portal.")),
            Map.entry(PROJECT_ARCHIVED, EmailTemplate.of(PROJECT_ARCHIVED,
                    "Project archived: {{project}}",
                    "SecPhils · Project update",
                    "Project archived: {{project}}",
                    "The project '{{project}}' has been archived by {{actor}}. It will be permanently removed on {{deleteDate}} unless restored earlier.",
                    "View the project",
                    "You're receiving this as a member of the project's company. Manage your notification preferences in the portal.")),
            Map.entry(PROJECT_RESTORED, EmailTemplate.of(PROJECT_RESTORED,
                    "Project restored: {{project}}",
                    "SecPhils · Project update",
                    "Project restored: {{project}}",
                    "The project '{{project}}' has been restored by {{actor}}.",
                    "View the project",
                    "You're receiving this as a member of the project's company. Manage your notification preferences in the portal.")),
            Map.entry(LANDING, EmailTemplate.of(LANDING,
                    "Landing page inquiry from {{firstName}} {{lastName}}",
                    "", "",
                    landingBodyDefault(),
                    "",
                    "Received via the SECPhils website contact form."))
    );

    /**
     * The stored template for {@code name} merged over the built-in default:
     * every blank/missing field keeps its default value. Unknown names (a
     * typo in the DB) fall back to the default with a warning.
     */
    public EmailTemplate resolve(String name) {
        EmailTemplate def = DEFAULTS.getOrDefault(name,
                new EmailTemplate(name, "", "", "", "", "", ""));
        try {
            SystemSettings settings = settingsRepository.findAll().stream().findFirst().orElse(null);
            if (settings == null || settings.getEmailTemplates() == null
                    || settings.getEmailTemplates().isBlank()) {
                return def;
            }
            JsonNode root = objectMapper.readTree(settings.getEmailTemplates());
            if (root.isArray()) {
                for (JsonNode node : root) {
                    String storedName = node.path("name").asText("");
                    if (!nameMatches(storedName, name)) continue;
                    boolean legacy = !storedName.equalsIgnoreCase(name);
                    String body = blankTo(node.path("body").asText(null), def.body());
                    if (legacy) body = migrateLegacyBody(body);
                    return new EmailTemplate(
                            name,
                            blankTo(node.path("subject").asText(null), def.subject()),
                            blankTo(node.path("kicker").asText(null), def.kicker()),
                            blankTo(node.path("heading").asText(null), def.heading()),
                            body,
                            blankTo(node.path("cta").asText(null), def.cta()),
                            blankTo(node.path("footer").asText(null), def.footer()));
                }
            }
            return def;
        } catch (Exception e) {
            log.warn("Could not read email_templates ({}); using defaults: {}", name, e.getMessage());
            return def;
        }
    }

    /**
     * Raw list of the admin's stored templates (for the Admin Settings UI
     * round-trip) — stored entries as-is, defaults for any known type the
     * admin has not saved yet, so the UI always shows the complete catalog.
     */
    public Map<String, EmailTemplate> catalog() {
        Map<String, EmailTemplate> out = new LinkedHashMap<>();
        for (String name : DEFAULTS.keySet()) {
            out.put(name, resolve(name));
        }
        return out;
    }

    // ------------------------------------------------------------ rendering

    /** Subject line with {{placeholders}} substituted (no escaping — subjects aren't HTML). */
    public String subject(String name, Map<String, String> vars) {
        return render(resolve(name).subject(), vars, false);
    }

    /** Small teal kicker line (HTML-escaped). */
    public String kicker(String name, Map<String, String> vars) {
        return render(resolve(name).kicker(), vars, true);
    }

    /** Card headline / h1 (HTML-escaped). */
    public String heading(String name, Map<String, String> vars) {
        return render(resolve(name).heading(), vars, true);
    }

    /** Card body: HTML-escaped, newlines → {@code <br>}, {@code **bold**} → {@code <strong>}. */
    public String bodyHtml(String name, Map<String, String> vars) {
        String rendered = render(resolve(name).body(), vars, true);
        return rendered.replace("\n", "<br>").replaceAll("\\*\\*([^*]+)\\*\\*", "<strong>$1</strong>");
    }

    /** CTA button label (HTML-escaped); blank = no button. */
    public String cta(String name, Map<String, String> vars) {
        return render(resolve(name).cta(), vars, true);
    }

    /** Footer note (HTML-escaped). */
    public String footer(String name, Map<String, String> vars) {
        return render(resolve(name).footer(), vars, true);
    }

    /**
     * The shared 560px branded card (white on #f4f5f7, teal #059669 accents)
     * every non-landing email uses. Pass an empty {@code ctaLabel} to omit
     * the button, a blank {@code kicker} to omit the kicker line.
     */
    public String brandedCard(String kickerText, String headingText, String bodyHtml,
                              String ctaLabel, String ctaLink, String footerText) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><body style=\"margin:0;padding:0;background:#f4f5f7;\">")
          .append("font-family:Arial,Helvetica,sans-serif;color:#1f2937;\">")
          .append("<div style=\"max-width:560px;margin:32px auto;padding:32px;background:#ffffff;\">")
          .append("border-radius:12px;border:1px solid #e5e7eb;\">");
        if (!kickerText.isEmpty()) {
            sb.append("<p style=\"margin:0 0 8px;font-size:13px;color:#059669;font-weight:bold;\">")
              .append(kickerText).append("</p>");
        }
        if (!headingText.isEmpty()) {
            sb.append("<h1 style=\"margin:0 0 16px;font-size:18px;font-weight:600;\">")
              .append(headingText).append("</h1>");
        }
        if (!bodyHtml.isEmpty()) {
            sb.append("<p style=\"margin:0 0 16px;font-size:14px;line-height:1.6;\">")
              .append(bodyHtml).append("</p>");
        }
        if (!ctaLabel.isEmpty() && ctaLink != null && !ctaLink.isEmpty()) {
            sb.append("<p style=\"margin:16px 0 0;\"><a href=\"").append(esc(ctaLink))
              .append("\" style=\"display:inline-block;background:#059669;color:#ffffff;\">")
              .append("padding:10px 18px;border-radius:8px;font-weight:bold;text-decoration:none;\">")
              .append(ctaLabel).append("</a></p>");
        }
        if (!footerText.isEmpty()) {
            sb.append("<p style=\"margin:16px 0 0;font-size:12px;color:#9ca3af;\">")
              .append(footerText).append("</p>");
        }
        sb.append("</div></body></html>");
        return sb.toString();
    }

    /**
     * Landing-page contact email: the template body is FULL HTML (its own
     * 600px card, independent of the branded shell). {{placeholders}} are
     * substituted raw — the admin owns the markup here, and the dynamic
     * values (name/email/phone/message) are HTML-escaped before substitution
     * so a visitor can't inject markup through the form.
     */
    public String landingContactHtml(Map<String, String> vars) {
        String body = resolve(LANDING).body();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '{' && i + 1 < body.length() && body.charAt(i + 1) == '{') {
                int close = body.indexOf("}}", i + 2);
                if (close > i) {
                    String key = body.substring(i + 2, close).trim();
                    String value = vars.getOrDefault(key, "");
                    if (key.equals("message")) {
                        value = esc(value).replace("\n", "<br>");
                    } else {
                        value = esc(value);
                    }
                    sb.append(value);
                    i = close + 1;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    /** Default full-HTML card for the landing contact email (the original hardcoded layout). */
    public static String landingBodyDefault() {
        return """
                <!DOCTYPE html>
                <html>
                <body style="margin:0;padding:0;background:#f4f5f7;font-family:Arial,Helvetica,sans-serif;color:#1f2937;">
                  <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background:#f4f5f7;padding:32px 0;">
                    <tr><td align="center">
                      <table role="presentation" width="600" cellpadding="0" cellspacing="0"
                             style="background:#ffffff;border-radius:12px;overflow:hidden;border:1px solid #e5e7eb;">
                        <tr><td style="background:#29ca8e;padding:24px 32px;">
                          <span style="color:#ffffff;font-size:20px;font-weight:bold;">New Website Inquiry</span>
                        </td></tr>
                        <tr><td style="padding:32px;">
                          <p style="margin:0 0 20px;font-size:14px;line-height:1.6;">
                            Someone submitted the contact form on your website. Details below.
                          </p>
                          <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="border:1px solid #e5e7eb;border-radius:8px;">
                            <tr><td style="padding:12px 16px;font-size:14px;"><strong>Full name:</strong> {{fullName}}</td></tr>
                            <tr><td style="padding:12px 16px;font-size:14px;background:#f9fafb;border-top:1px solid #e5e7eb;"><strong>Email:</strong> {{email}}</td></tr>
                            <tr><td style="padding:12px 16px;font-size:14px;border-top:1px solid #e5e7eb;"><strong>Phone:</strong> {{phone}}</td></tr>
                            <tr><td style="padding:12px 16px;font-size:14px;background:#f9fafb;border-top:1px solid #e5e7eb;"><strong>How can we help?</strong><br>{{message}}</td></tr>
                          </table>
                          <p style="margin:20px 0 0;font-size:13px;color:#6b7280;">
                            Reply directly to reach this visitor: <a href="mailto:{{email}}" style="color:#29ca8e;">{{email}}</a>
                          </p>
                        </td></tr>
                        <tr><td style="padding:16px 32px;background:#f9fafb;border-top:1px solid #e5e7eb;">
                          <p style="margin:0;font-size:12px;color:#9ca3af;">Received via the SECPhils website contact form.</p>
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>""";
    }

    // ------------------------------------------------------------- helpers

    private static String render(String template, Map<String, String> vars, boolean escapeValues) {
        if (template == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < template.length(); i++) {
            char c = template.charAt(i);
            if (c == '{' && i + 1 < template.length() && template.charAt(i + 1) == '{') {
                int close = template.indexOf("}}", i + 2);
                if (close > i) {
                    String key = template.substring(i + 2, close).trim();
                    String value = vars.getOrDefault(key, "");
                    sb.append(escapeValues ? esc(value) : value);
                    i = close + 1;
                    continue;
                }
            }
            sb.append(c);
        }
        return sb.toString();
    }

    static String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String blankTo(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    /**
     * Name matching with one legacy alias: pre-catalog rows stored the
     * editable invite template under "Team Invitation" (the only one of the
     * old three UI entries the app actually sent). It maps onto {@code invite};
     * the other legacy entries ("Welcome Email", "Project Update") were
     * never sent and have no live counterpart.
     */
    private static boolean nameMatches(String storedName, String wanted) {
        if (storedName.equalsIgnoreCase(wanted)) return true;
        return INVITE.equals(wanted)
                && (storedName.equalsIgnoreCase("Team Invitation") || storedName.equalsIgnoreCase("Invite"));
    }

    /**
     * One-time migration for legacy "Team Invitation" bodies: the old body
     * carried the setup link as an inline "{{setupLink}}" placeholder. The
     * new card carries the same link as the CTA button, so any line still
     * referencing the placeholder is dropped rather than rendered blank.
     */
    private static String migrateLegacyBody(String body) {
        if (body == null || !body.contains("{{setupLink}}")) return body;
        StringBuilder sb = new StringBuilder();
        for (String line : body.split("\n", -1)) {
            if (line.contains("{{setupLink}}")) continue;
            sb.append(line).append('\n');
        }
        return sb.toString();
    }
}
