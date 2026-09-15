package com.secphils.controller;

import com.secphils.common.ApiException;
import com.secphils.common.AuditService;
import com.secphils.entity.NotificationPreference;
import com.secphils.entity.User;
import com.secphils.repository.NotificationPreferenceRepository;
import com.secphils.repository.UserRepository;
import com.secphils.service.EmailSuppressionService;
import com.secphils.service.MailService;
import com.secphils.service.NotificationPrefs;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tokenized email-preference page (V40) — the target of the
 * "manage email preferences" link and the RFC 8058 List-Unsubscribe header
 * in every notification email. No login: access is the stable per-user
 * token (users.unsubscribe_token, minted on first send).
 *
 * GET  /api/v1/email/preferences?t=…  → branded HTML page (server-rendered,
 *      no JS — it must render inside any webmail's in-app browser).
 * POST same URL, form-encoded: "action=save" persists the matrix (email
 * column only — in-app bell is untouched); "action=unsubscribe" or the
 * RFC-mandated "List-Unsubscribe=One-Click" body turns the whole email
 * column off. Mandatory emails (invites, access changes, security) live
 * outside this key set and keep flowing either way — the page says so.
 *
 * Self-contained HTML instead of an SPA route: email links must work with
 * JS blocked, cookies cleared, or cookies from a logged-in session leaking
 * the wrong account into the page.
 */
@RestController
@RequestMapping("/api/v1/email")
public class EmailPreferencesController {

    private final UserRepository users;
    private final NotificationPreferenceRepository preferences;
    private final EmailSuppressionService suppressions;
    private final NotificationPrefs prefs;
    private final MailService mail;
    private final AuditService auditService;

    public EmailPreferencesController(UserRepository users,
                                      NotificationPreferenceRepository preferences,
                                      EmailSuppressionService suppressions,
                                      NotificationPrefs prefs,
                                      MailService mail,
                                      AuditService auditService) {
        this.users = users;
        this.preferences = preferences;
        this.suppressions = suppressions;
        this.prefs = prefs;
        this.mail = mail;
        this.auditService = auditService;
    }

    @GetMapping(value = "/preferences", produces = MediaType.TEXT_HTML_VALUE)
    @Transactional(readOnly = true)
    public ResponseEntity<String> page(@RequestParam(value = "t", required = false) String token) {
        User user = resolve(token);
        if (user == null) return html(invalidPage());
        Map<String, Boolean> email = currentEmailPrefs(user);
        return html(renderPage(user, email, null));
    }

    @PostMapping(value = "/preferences", produces = MediaType.TEXT_HTML_VALUE)
    @Transactional
    public ResponseEntity<String> submit(@RequestParam(value = "t", required = false) String token,
                                         @RequestParam(value = "action", required = false) String action,
                                         @RequestParam(value = "List-Unsubscribe", required = false) String oneClick,
                                         @RequestParam(value = "cat", required = false) String[] cats,
                                         HttpServletRequest http) {
        User user = resolve(token);
        if (user == null) return html(invalidPage());

        // RFC 8058 one-click clients POST the token URL with a form body of
        // "List-Unsubscribe=One-Click". A body we cannot interpret (unknown
        // content type, empty body) must NEVER be read as "everything off" —
        // it falls through to a plain page render.
        boolean oneClickOff = oneClick != null && oneClick.equalsIgnoreCase("One-Click");
        boolean allOff = oneClickOff || "unsubscribe".equalsIgnoreCase(action);
        boolean explicitSave = "save".equalsIgnoreCase(action);
        if (!allOff && !explicitSave) {
            return html(renderPage(user, currentEmailPrefs(user), null));
        }

        Map<String, Boolean> email = prefs.defaults();
        if (allOff) {
            email.replaceAll((k, v) -> false);
        } else {
            for (String k : prefs.KEYS) email.put(k, false);
            if (cats != null) {
                for (String c : cats) {
                    if (prefs.isKnown(c)) email.put(c, true);
                }
            }
        }

        NotificationPreference pref = preferences.findByUserId(user.getId())
                .orElseGet(() -> {
                    NotificationPreference p = new NotificationPreference();
                    p.setUserId(user.getId());
                    return p;
                });
        pref.setEmail(prefs.writeJson(email));
        preferences.save(pref);

        auditService.audit(null, allOff ? "EMAIL_UNSUBSCRIBE_ALL" : "EMAIL_PREF_UPDATE",
                "NotificationPreference", user.getId(),
                "Email: " + user.getEmail() + " — " + prefs.writeJson(email), http);

        return html(renderPage(user, email, allOff
                ? "You have been unsubscribed from all notification emails. Your in-app alerts stay on, and you can turn email back on any time in Settings → Notifications."
                : "Your email preferences were saved."));
    }

    private User resolve(String token) {
        if (token == null || token.isBlank()) return null;
        return users.findByUnsubscribeToken(token.trim())
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                .orElse(null);
    }

    private Map<String, Boolean> currentEmailPrefs(User user) {
        return preferences.findByUserId(user.getId())
                .map(p -> prefs.merged(p.getEmail()))
                .orElseGet(prefs::defaults);
    }

    // ---------- self-contained branded HTML (matches the email card palette) ----------

    private ResponseEntity<String> html(String body) {
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(body);
    }

    private String esc(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String invalidPage() {
        return frame("<h1>Invalid or deactivated link</h1>"
                + "<p>This email-preferences link is no longer valid. If you still have a SECPhils portal account, "
                + "sign in and manage notifications under Settings &rarr; Notifications.</p>");
    }

    private String renderPage(User user, Map<String, Boolean> email, String notice) {
        StringBuilder rows = new StringBuilder();
        for (String key : prefs.KEYS) {
            boolean on = Boolean.TRUE.equals(email.get(key));
            rows.append("<tr><td style=\"padding:10px 0;border-bottom:1px solid #f1f2f4;font-size:14px;\">")
                .append(esc(prefs.label(key))).append("</td>")
                .append("<td style=\"padding:10px 0;border-bottom:1px solid #f1f2f4;text-align:right;\">")
                .append("<input type=\"checkbox\" name=\"cat\" value=\"").append(esc(key))
                .append("\"").append(on ? " checked" : "").append(" style=\"width:16px;height:16px;accent-color:#059669;\">")
                .append("</td></tr>");
        }
        String noticeHtml = notice == null ? ""
                : "<p style=\"margin:0 0 16px;padding:10px 14px;background:#ecfdf5;border:1px solid #a7f3d0;"
                  + "border-radius:8px;font-size:13px;color:#065f46;\">" + esc(notice) + "</p>";
        return frame(noticeHtml
                + "<p style=\"margin:0 0 4px;font-size:13px;color:#059669;font-weight:bold;\">SECPHILS PORTAL</p>"
                + "<h1>Email notifications for " + esc(user.getEmail()) + "</h1>"
                + "<p style=\"margin:0 0 16px;font-size:14px;line-height:1.6;\">Choose which notification emails you "
                + "want to receive. <b>These emails only</b> — your in-app bell notifications are not affected. "
                + "Account emails (invites, access changes, security notices) are always sent.</p>"
                + "<form method=\"POST\" action=\"?t=" + esc(user.getUnsubscribeToken()) + "\">"
                + "<table style=\"width:100%;border-collapse:collapse;\">" + rows + "</table>"
                + "<p style=\"margin:20px 0 0;\">"
                + "<button type=\"submit\" name=\"action\" value=\"save\" style=\"background:#059669;color:#fff;"
                + "border:0;padding:10px 18px;border-radius:8px;font-weight:bold;font-size:14px;cursor:pointer;\">Save preferences</button>"
                + " <button type=\"submit\" name=\"action\" value=\"unsubscribe\" style=\"background:#fff;color:#b91c1c;"
                + "border:1px solid #fecaca;padding:10px 18px;border-radius:8px;font-size:14px;cursor:pointer;\">Unsubscribe from all</button>"
                + "</p></form>"
                + "<p style=\"margin:24px 0 0;font-size:12px;color:#9ca3af;\">Wrong address? This page was opened from a link in a "
                + "SECPhils email. You can also manage the same switches, per category and channel, in the portal under "
                + "Settings &rarr; Notifications.</p>");
    }

    private String frame(String inner) {
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>SECPhils — Email notifications</title></head>"
                + "<body style=\"margin:0;padding:0;background:#f4f5f7;font-family:Arial,Helvetica,sans-serif;color:#1f2937;\">"
                + "<div style=\"max-width:560px;margin:32px auto;padding:32px;background:#fff;"
                + "border-radius:12px;border:1px solid #e5e7eb;\">" + inner + "</div></body></html>";
    }
}
