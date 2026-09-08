package com.secphils.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secphils.entity.Notification;
import com.secphils.entity.NotificationPreference;
import com.secphils.entity.Project;
import com.secphils.entity.Review;
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
 * Review-submission fan-out — PROVIDER SIDE ONLY (Jaybar's rule: when a
 * customer submits a project review, the provider team hears about it; the
 * customer who wrote it never needs a copy, and no other client is told
 * anything — a review is between its author and the provider).
 *
 * <p>Recipients: every active provider staff account (ADMIN/USER), the actor
 * skipped. Each gets an in-app bell row ({@code REVIEW_SUBMITTED}, entityType
 * {@code Review} → bell deep-links /reviews) plus a branded email, each
 * channel gated independently by the shared "reviewSubmitted" preference
 * (missing pref/key = allowed), mirroring every other fan-out. Email failures
 * are logged, never thrown.
 *
 * <p>Fires on review CREATION only. Status transitions (PENDING → ACKNOWLEDGED
 * etc.) are provider-internal bookkeeping performed BY staff for staff —
 * notifying them of their own clicks is exactly the ceremonial noise this
 * portal deliberately avoids.
 */
@Service
public class ReviewNotificationService {

    private static final Logger log = LoggerFactory.getLogger(ReviewNotificationService.class);
    private static final String PREF_KEY = "reviewSubmitted";

    private final UserRepository users;
    private final NotificationRepository notifications;
    private final NotificationPreferenceRepository preferences;
    private final MailService mail;
    private final EmailTemplateService templateService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String portalBaseUrl;

    public ReviewNotificationService(UserRepository users,
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

    @Transactional
    public void onReviewSubmitted(Review review, Long actorId) {
        Project project = review.getProject();
        String projectName = project != null && project.getName() != null ? project.getName() : "a project";
        String companyName = project != null && project.getCompany() != null
                && project.getCompany().getName() != null ? project.getCompany().getName() : "";
        User reviewer = review.getCustomerUser();
        String reviewerName = reviewer != null
                ? (reviewer.getFullName() == null || reviewer.getFullName().isBlank()
                        ? reviewer.getEmail() : reviewer.getFullName())
                : "A customer";
        String rating = ratingLabel(review.getRating());
        String link = portalLink("reviews");

        for (User u : providerStaff()) {
            if (u.getId().equals(actorId)) continue;
            NotificationPreference pref = preferences.findByUserId(u.getId()).orElse(null);
            Map<String, String> vars = Map.of(
                    "name", u.getFirstName() == null || u.getFirstName().isBlank() ? "there" : u.getFirstName(),
                    "reviewer", reviewerName,
                    "project", projectName,
                    "company", companyName,
                    "rating", review.getRating() == null ? "—" : String.valueOf(review.getRating()),
                    "ratingLabel", rating);

            if (prefAllows(pref == null ? null : pref.getInApp())) {
                Notification n = new Notification();
                User ref = new User();
                ref.setId(u.getId());
                n.setRecipient(ref);
                n.setTitle("New review — " + projectName);
                n.setBody(reviewerName + " rated " + projectName + " " + rating
                        + ": " + (review.getTitle() == null ? "" : review.getTitle()));
                n.setType("REVIEW_SUBMITTED");
                n.setEntityType("Review");
                n.setEntityId(review.getId());
                n.setIsRead(false);
                n.setCreatedAt(LocalDateTime.now());
                notifications.save(n);
            }

            String email = u.getEmail();
            if (email != null && !email.isBlank() && prefAllows(pref == null ? null : pref.getEmail())) {
                try {
                    mail.sendHtml(email,
                            templateService.subject(EmailTemplateService.REVIEW_SUBMITTED, vars),
                            templateService.brandedCard(
                                    templateService.kicker(EmailTemplateService.REVIEW_SUBMITTED, vars),
                                    templateService.heading(EmailTemplateService.REVIEW_SUBMITTED, vars),
                                    templateService.bodyHtml(EmailTemplateService.REVIEW_SUBMITTED, vars),
                                    templateService.cta(EmailTemplateService.REVIEW_SUBMITTED, vars),
                                    link,
                                    templateService.footer(EmailTemplateService.REVIEW_SUBMITTED, vars)),
                            link);
                } catch (Exception e) {
                    log.warn("Review email to {} failed: {}", email, e.getMessage());
                }
            }
        }
    }

    private static String ratingLabel(Integer rating) {
        if (rating == null) return "—";
        return rating == 1 ? "1 star" : rating + " stars";
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
