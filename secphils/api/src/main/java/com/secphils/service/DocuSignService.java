package com.secphils.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.secphils.dto.DocuSignConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

/**
 * DocuSign JWT-grant connectivity probe (V34). Builds the RS256-signed JWT
 * assertion the way DocuSign's JWT grant expects and exchanges it at
 * /oauth/auth/token — a 200 with an access_token proves the private key,
 * integration key, account and user ids all line up. JDK-only (no SDK):
 * no signing feature ships on this config yet, only staging credentials +
 * a live test. Failures return {ok:false, message} — never throw into the
 * admin UI.
 */
@Service
public class DocuSignService {

    private static final Logger log = LoggerFactory.getLogger(DocuSignService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public DocuSignConfig parse(String json) {
        try {
            return objectMapper.readValue(json, DocuSignConfig.class);
        } catch (Exception e) {
            return new DocuSignConfig();
        }
    }

    public String serialize(DocuSignConfig cfg) {
        try {
            return objectMapper.writeValueAsString(cfg);
        } catch (Exception e) {
            throw new IllegalStateException("DocuSign config not serializable", e);
        }
    }

    public Map<String, Object> testConnection(DocuSignConfig cfg) {
        if (!cfg.isConfigured()) {
            return Map.of("ok", false,
                    "message", "Integration key, account id, user id and private key are all required.");
        }
        try {
            String assertion = buildJwtAssertion(cfg);
            String base = cfg.oauthBaseUrl == null || cfg.oauthBaseUrl.isBlank()
                    ? "https://account-docusign.com" : cfg.oauthBaseUrl.trim();
            String form = "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion="
                    + URLEncoder.encode(assertion, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder(URI.create(base + "/oauth/auth/token"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200) {
                var body = objectMapper.readTree(res.body());
                if (body.has("access_token")) {
                    log.info("DocuSign connection test OK (account {})", cfg.accountId);
                    return Map.of("ok", true,
                            "message", "Connected — DocuSign issued an access token for account " + cfg.accountId
                                    + " (" + base.replace("https://", "") + ").");
                }
            }
            String detail = res.body() == null ? "" : res.body().replaceAll("\\s+", " ").trim();
            if (detail.length() > 180) detail = detail.substring(0, 180) + "…";
            return Map.of("ok", false,
                    "message", "DocuSign rejected the request (HTTP " + res.statusCode() + "): " + detail);
        } catch (Exception e) {
            return Map.of("ok", false,
                    "message", "DocuSign test failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    private String buildJwtAssertion(DocuSignConfig cfg) throws Exception {
        PrivateKey key = loadPrivateKey(cfg.privateKey);
        Instant now = Instant.now();
        String header = b64url("{\"alg\":\"RS256\",\"typ\":\"JWT\"}");
        var claims = objectMapper.createObjectNode();
        claims.put("iss", cfg.integrationKey);
        claims.put("sub", cfg.userId);
        claims.put("aud", baseHost(cfg.oauthBaseUrl));
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plus(Duration.ofMinutes(10)).getEpochSecond());
        var scope = claims.putArray("scope");
        scope.add("signature");
        scope.add("impersonation");
        String payload = b64url(objectMapper.writeValueAsString(claims));
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(key);
        sig.update((header + "." + payload).getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sig.sign());
    }

    private static String baseHost(String oauthBaseUrl) {
        String base = oauthBaseUrl == null || oauthBaseUrl.isBlank()
                ? "account-docusign.com" : oauthBaseUrl.trim().replaceAll("^https?://", "").replaceAll("/+$", "");
        return base;
    }

    /** Accepts PEM ("-----BEGIN ... PRIVATE KEY-----") or raw base64 PKCS#8. */
    private static PrivateKey loadPrivateKey(String pem) throws Exception {
        String body = pem.replaceAll("-----BEGIN ([A-Z ]*)-----", "")
                .replaceAll("-----END ([A-Z ]*)-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getMimeDecoder().decode(body);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static String b64url(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
}
