package com.secphils.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secphils.entity.Company;
import com.secphils.entity.Notification;
import com.secphils.entity.NotificationPreference;
import com.secphils.entity.User;
import com.secphils.repository.NotificationPreferenceRepository;
import com.secphils.repository.NotificationRepository;
import com.secphils.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Team-member removal fan-out. When a company's authorized representative
 * removes a teammate's portal access (Settings → Team &amp; Invitations), the
 * obligation is one-sided: the removed member loses login immediately and the
 * rep gets an audit trail. Without an event, the removed member just finds the
 * door locked and the company has no record that they tried. This service
 * closes that gap, mirroring the rep-change fan-out:
 *
 * - the REMOVED member gets a {@code teamAccessRemoved} email + bell (the
 *   channel is forced open for them regardless of preferences — it is the
 *   notice explaining why their access died, not a promotional message);
 * - the ACTING REP gets a {@code teamMemberRemoved} bell row (not an email —
 *   they initiated it; the row is durable history in the drawer);
 * - provider staff (ADMIN/USER, actor skipped) get an in-app row so the
 *   customer cutting a teammate off is visible internally.
 *
 * Every recipient except the removed member is gated by their
 * "teamMemberRemoved" preference (missing pref/key = allowed), same as every
 * other fan-out. Mail failures are logged, never thrown.
 */
@Service
public class TeamRemovalNotificationService {

    private static final Logger log = LoggerFactory.getLogger(TeamRemovalNotificationService.class);
    private static final String PREF_KEY = "teamMemberRemoved";

    private final UserRepository users;
    private final NotificationRepository notifications;
    private final NotificationPreferenceRepository preferences;
    private final MailService mail;
    private final EmailTemplateService templateService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String portalBaseUrl;

    public TeamRemovalNotificationService(UserRepository users,
                                          NotificationRepository notifications,
                                          NotificationPreferenceRepository preferences,
                                          MailService mail,
                                          EmailTemplateService templateService,
                                          @Value("${app.invite.base-url:http://localhost:3000}") String portalBaseUrl) {
        this.users = users;
        this.notifications = notifications;
        this.preferences = preferences;
        this.mail = mail;
        this.templateService = templateService;
        this.portalBaseUrl = portalBaseUrl;
    }

    /**
     * Announce a completed team-member removal. {@code removed} loses access
     * and is told so (email + bell, preferences overridden — see class doc);
     * {@code rep} gets the confirming bell; provider staff get an in-app row.
     */
    @Transactional
    public void onMemberRemoved(Company company, User removed, User rep, Long actorId) {
        String companyName = company.getName() == null ? "your company" : company.getName();
        String settingsLink = portalLink("settings");
        String firstName = removed.getFirstName() == null || removed.getFirstName().isBlank()
                ? removed.getFullName() : removed.getFirstName();

        // 1) The removed member — email + bell, channel gates bypassed.
        Map<String, String> vars = Map.of(
                "name", firstName == null ? "there" : firstName,
                "company", companyName);
        notifyUser(removed, null, true, true,
                "Your portal access for " + companyName + " was removed",
                "Contact your SECPhils representative if you believe this is a mistake.",
                "TEAM_ACCESS_REMOVED", company.getId(), settingsLink,
                EmailTemplateService.TEAM_ACCESS_REMOVED, vars);

        // 2) The acting rep — confirming bell row only (no email to yourself
        //    about an action you just took; the row is the durable history).
        String removedLabel = removed.getFullName() == null || removed.getFullName().isBlank()
                ? removed.getEmail() : removed.getFullName();
        notifyUser(rep, null, false, false,
                removedLabel + " removed from the team",
                "Their portal access for " + companyName + " was cut off just now.",
                "TEAM_MEMBER_REMOVED", company.getId(), settingsLink,
                null, Map.of());

        // 3) Provider staff — in-app only (a customer cutting a teammate off
        //    is an operational fact the provider should see).
        Map<String, String> staffVars = Map.of(
                "company", companyName,
                "removedName", removedLabel,
                "removedEmail", removed.getEmail());
        for (User u : providerStaff()) {
            if (u.getId().equals(actorId)) continue;
            notifyUser(u, null, false, false,
                    "Team member removed — " + companyName,
                    removedLabel + " (" + removed.getEmail() + ") no longer has portal access",
                    "TEAM_MEMBER_REMOVED", company.getId(), settingsLink,
                    null, staffVars);
        }
    }

    /** One recipient: bell row + (when {@code withEmail}) email. */
    private void notifyUser(User u, Long skipIfSameId, boolean withEmail, boolean ignorePrefs,
                            String bellTitle, String bellBody, String type,
                            Long companyId, String link, String templateName,
                            Map<String, String> vars) {
        if (u == null || (skipIfSameId != null && u.getId().equals(skipIfSameId))) return;
        NotificationPreference pref = preferences.findByUserId(u.getId()).orElse(null);
        boolean allowInApp = ignorePrefs || prefAllows(pref == null ? null : pref.getInApp());
        boolean allowEmail = ignorePrefs || prefAllows(pref == null ? null : pref.getEmail());
        if (allowInApp) {
            Notification n = new Notification();
            User ref = new User();
            ref.setId(u.getId());
            n.setRecipient(ref);
            n.setTitle(bellTitle);
            n.setBody(bellBody);
            n.setType(type);
            n.setEntityType("Company");
            n.setEntityId(companyId);
            n.setIsRead(false);
            n.setCreatedAt(LocalDateTime.now());
            notifications.save(n);
        }
        if (withEmail && allowEmail && templateName != null && u.getEmail() != null && !u.getEmail().isBlank()) {
            try {
                mail.sendHtml(u.getEmail(),
                        templateService.subject(templateName, vars),
                        templateService.brandedCard(
                                templateService.kicker(templateName, vars),
                                templateService.heading(templateName, vars),
                                templateService.bodyHtml(templateName, vars),
                                templateService.cta(templateName, vars),
                                link,
                                templateService.footer(templateName, vars)),
                        link);
            } catch (Exception e) {
                log.warn("Team-removal email to {} failed: {}", u.getEmail(), e.getMessage());
            }
        }
    }

    /** Reads a stored preference flag; missing rows / bad JSON fall back to default-ON. */
    private boolean prefAllows(String channelJson) {
        try {
            if (channelJson == null || channelJson.isBlank()) return true;
            Map<?, ?> m = objectMapper.readValue(channelJson, Map.class);
            Object v = m.get(PREF_KEY);
            return v == null || Boolean.TRUE.equals(v);
        } catch (Exception e) {
            return true;
        }
    }

    private List<User> providerStaff() {
        return users.findAll().stream()
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                .filter(u -> "ADMIN".equals(u.getRole()) || "USER".equals(u.getRole()))
                .toList();
    }

    private String portalLink(String path) {
        String base = portalBaseUrl.endsWith("/")
                ? portalBaseUrl.substring(0, portalBaseUrl.length() - 1) : portalBaseUrl;
        return base + "/" + path;
    }
}
