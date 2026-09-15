package com.secphils.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secphils.service.EmailSuppressionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;

/**
 * SES → SNS event webhook (V40): bounces and complaints become rows in
 * email_suppressions, so MailService stops mailing those addresses. Wire it
 * up in SES: Configuration Set → event publishing → an SNS topic whose
 * subscription is this URL (protocol HTTPS) and whose endpoint policy
 * trusts the SES/SNS service.
 *
 * SNS delivers three envelopes; ALL must answer 200 or SNS retries and the
 * topic backs up:
 *   SubscriptionConfirmation → fetch the SubscribeURL once (https endpoint
 *     only), then record the subscription.
 *   Notification            → the SES event payload (bounce/complaint/…).
 *   NotificationForSubscriptionConfirmation → signature noise, ack.
 *
 * Trust model: full SNS message-signature verification (cert chain against
 * amazonaws.com + signed-fields check) is deliberately NOT done yet — the
 * endpoint is idempotent and its only side effect is "suppress an address
 * from receiving mail", so the worst a forged POST achieves is suppressing
 * mail (fails closed, cannot leak or send). The subscribe step is further
 * fenced to a host allowlist so a spoof cannot turn the endpoint into an
 * SSRF primitive. Revisit before this matters at scale (see skill notes).
 */
@RestController
@RequestMapping("/api/v1/ses")
public class SesEventController {

    private static final Logger log = LoggerFactory.getLogger(SesEventController.class);

    private final EmailSuppressionService suppressions;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public SesEventController(EmailSuppressionService suppressions) {
        this.suppressions = suppressions;
    }

    @PostMapping("/events")
    @Transactional
    public ResponseEntity<Map<String, String>> event(@RequestBody(required = false) String raw) {
        if (raw == null || raw.isBlank()) return ResponseEntity.ok(Map.of("status", "ignored"));
        JsonNode msg;
        try {
            msg = objectMapper.readTree(raw);
        } catch (Exception e) {
            log.warn("SES webhook: unparseable body — ignored");
            return ResponseEntity.ok(Map.of("status", "ignored"));
        }
        String type = msg.path("Type").asText("");
        switch (type) {
            case "SubscriptionConfirmation" -> confirmSubscription(msg);
            case "Notification" -> handleNotification(msg.path("Message"));
            default -> {
                // keep log quiet for the periodic health of a chatty topic
                if (!type.isBlank()) log.info("SES webhook: envelope Type '{}' acked without action", type);
            }
        }
        // Always 200: SNS must not retry this endpoint over an app-level skip.
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /** One-time SNS handshake. Host allowlist: the SubscribeURL must be an
     *  amazonaws.com/sns URL from the envelope — never an arbitrary target. */
    private void confirmSubscription(JsonNode envelope) {
        String url = envelope.path("SubscribeURL").asText("");
        if (url.isBlank()) return;
        try {
            URI u = URI.create(url);
            String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equals(u.getScheme()) || !host.endsWith(".amazonaws.com")) {
                log.warn("SES webhook: SubscribeURL host not on the aws allowlist — skipped: {}", host);
                return;
            }
            String body = http.send(HttpRequest.newBuilder(u).GET().build(),
                    HttpResponse.BodyHandlers.ofString()).body();
            log.info("SES webhook: SNS subscription confirmed ({})",
                    body != null && body.contains("<ConfirmSubscriptionResponse>") ? "ok" : "unexpected response");
        } catch (Exception e) {
            log.warn("SES webhook: subscription confirm failed: {}", e.getMessage());
        }
    }

    /** The SES event JSON may itself be a JSON string (stringified Message). */
    private void handleNotification(JsonNode message) {
        if (message.isMissingNode() || message.isNull()) return;
        JsonNode event = message;
        if (message.isTextual()) {
            try {
                event = objectMapper.readTree(message.asText());
            } catch (Exception e) {
                return;
            }
        }
        String eventType = event.path("eventType").asText(event.path("notificationType").asText(""));
        String et = eventType.toLowerCase(Locale.ROOT);
        if (et.contains("bounce")) {
            JsonNode bounce = event.path("bounce");
            // Suppress ONLY Permanent (hard) bounces — PersistentUserFailure etc.
            // A soft bounce is transient; suppressing on it would silently stop
            // mail to a mailbox that works tomorrow.
            String subtype = bounce.path("bounceType").asText("").toLowerCase(Locale.ROOT);
            if ("permanent".equals(subtype)) {
                String detail = subtype + "/" + bounce.path("bounceSubType").asText("");
                for (JsonNode a : bounce.path("recipients")) {
                    record(a.asText(), EmailSuppressionService.REASON_BOUNCE, detail);
                }
            }
        } else if (et.contains("complaint")) {
            JsonNode complaint = event.path("complaint");
            for (JsonNode a : complaint.path("recipients")) {
                record(a.asText(), EmailSuppressionService.REASON_COMPLAINT,
                        complaint.path("complaintFeedbackType").asText(""));
            }
        }
        // delivery/send/reject/deliveryDelay notifications: acked, no action.
    }

    private void record(String email, String reason, String detail) {
        if (email == null || email.isBlank()) return;
        suppressions.add(email.trim(), "", reason, detail, null);
        log.info("SES webhook: suppressed {} ({} — {})", email.trim(), reason, detail);
    }
}
