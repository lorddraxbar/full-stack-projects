package com.secphils.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secphils.dto.GoogleSsoConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Google SSO via the standard OAuth 2.0 authorization-code flow.
 *
 *   1. POST /auth/sso/google/authorize  -> 302 redirect to accounts.google.com
 *   2. Google redirects the browser to {redirectUri}?code=...&state=...
 *   3. The frontend calls POST /auth/sso/google/callback with the code
 *   4. We exchange the code for an id_token + profile at Google's token
 *      endpoint, verify the id_token signature via Google's JWKS
 *      (https://www.googleapis.com/oauth2/v3/certs), and only then
 *      sign the user in (linking by verified email when possible).
 *
 * The old "trust a client-submitted email" stub has been removed —
 * identities are established exclusively from Google-verified claims.
 */
@Service
public class SsoService {

    private static final Logger log = LoggerFactory.getLogger(SsoService.class);
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String JWKS_URL = "https://www.googleapis.com/oauth2/v3/certs";
    private static final String SCOPE = "openid email profile";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = buildRestTemplate();
    private volatile JsonNode cachedJwks;
    private volatile long jwksFetchedAt;

    private static RestTemplate buildRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(15_000);
        return new RestTemplate(factory);
    }

    /** Authorization URL to redirect the browser to. */
    public String buildAuthorizeUrl(GoogleSsoConfig cfg, String state) {
        return UriComponentsBuilder.fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", cfg.clientId)
                .queryParam("redirect_uri", cfg.redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPE)
                .queryParam("state", state)
                .queryParam("prompt", "select_account")
                .build().toUriString();
    }

    /**
     * Exchange the authorization code with Google and return the verified
     * identity claims. Throws IllegalArgumentException with a user-facing
     * message on any failure (invalid/expired code, bad signature,
     * domain mismatch).
     */
    public Map<String, String> exchangeCode(GoogleSsoConfig cfg, String code) throws Exception {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("code", code);
        form.put("client_id", cfg.clientId);
        form.put("client_secret", cfg.clientSecret);
        form.put("redirect_uri", cfg.redirectUri);
        form.put("grant_type", "authorization_code");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        String body = form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                        + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .reduce((a, b) -> a + "&" + b).orElse("");
        String resp = restTemplate.postForObject(TOKEN_URL,
                new HttpEntity<>(body, headers), String.class);
        if (resp == null) throw new IllegalArgumentException("Google token endpoint returned no response");

        JsonNode token = objectMapper.readTree(resp);
        if (token.has("error")) {
            log.warn("Google token exchange failed: {} {}", token.get("error"), token.get("error_description"));
            throw new IllegalArgumentException("Google did not accept the authorization code: " + token.get("error"));
        }
        String idToken = token.path("id_token").asText("");
        if (idToken.isBlank()) throw new IllegalArgumentException("Google response missing id_token");

        // Verify signature + claims via Google's public JWKS.
        JsonNode idClaims = verifyIdToken(idToken, cfg.clientId);

        // Enforce domain restriction if configured (e.g. "secphils.com").
        String email = idClaims.path("email").asText("").toLowerCase(Locale.ROOT);
        String domain = cfg.domainRestriction == null ? "" : cfg.domainRestriction.trim().toLowerCase(Locale.ROOT);
        if (!domain.isBlank() && !email.endsWith("@" + domain)) {
            throw new IllegalArgumentException("This Google account is not permitted (" + domain + " only)");
        }

        Map<String, String> claims = new LinkedHashMap<>();
        claims.put("email", email);
        claims.put("name", idClaims.path("name").asText(""));
        claims.put("given_name", idClaims.path("given_name").asText(""));
        claims.put("family_name", idClaims.path("family_name").asText(""));
        return claims;
    }

    /** RSA-SHA256 verification of a Google id_token against the JWKS. */
    private JsonNode verifyIdToken(String idToken, String expectedAud) throws Exception {
        String[] parts = idToken.split("\\.");
        if (parts.length != 3) throw new IllegalArgumentException("Malformed id_token");
        JsonNode header = objectMapper.readTree(base64UrlDecode(parts[0]));
        JsonNode payload = objectMapper.readTree(base64UrlDecode(parts[1]));
        byte[] signature = base64UrlDecode(parts[2]);

        JsonNode jwks = fetchJwks();
        String kid = header.path("kid").asText("");
        JsonNode keyNode = null;
        for (JsonNode k : jwks.path("keys")) {
            if (k.path("kid").asText("").equals(kid)) { keyNode = k; break; }
        }
        if (keyNode == null) throw new IllegalArgumentException("Unknown Google signing key: " + kid);

        java.security.PublicKey pubKey = buildRsaPublicKey(keyNode);
        java.security.Signature sig = java.security.Signature.getInstance("SHA256withRSA");
        sig.initVerify(pubKey);
        sig.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
        if (!sig.verify(signature)) throw new IllegalArgumentException("id_token signature verification failed");

        long now = System.currentTimeMillis() / 1000;
        if (payload.path("exp").asLong(0) < now) throw new IllegalArgumentException("id_token expired");
        String iss = payload.path("iss").asText("");
        if (!iss.equals("https://accounts.google.com") && !iss.equals("accounts.google.com")) {
            throw new IllegalArgumentException("id_token issuer mismatch");
        }
        String aud = payload.path("aud").asText("");
        if (!aud.equals(expectedAud)) throw new IllegalArgumentException("id_token audience mismatch");
        return payload;
    }

    private JsonNode fetchJwks() throws Exception {
        if (cachedJwks == null || System.currentTimeMillis() - jwksFetchedAt > 10 * 60 * 1000) {
            String body = restTemplate.getForObject(JWKS_URL, String.class);
            cachedJwks = objectMapper.readTree(body);
            jwksFetchedAt = System.currentTimeMillis();
        }
        return cachedJwks;
    }

    private java.security.PublicKey buildRsaPublicKey(JsonNode key) throws Exception {
        byte[] n = base64UrlDecode(key.path("n").asText());
        byte[] e = base64UrlDecode(key.path("e").asText());
        java.security.spec.RSAPublicKeySpec spec = new java.security.spec.RSAPublicKeySpec(
                new java.math.BigInteger(1, n), new java.math.BigInteger(1, e));
        return java.security.KeyFactory.getInstance("RSA").generatePublic(spec);
    }

    private static byte[] base64UrlDecode(String s) throws Exception {
        return java.util.Base64.getUrlDecoder().decode(s);
    }

    // ---- JSONB column helpers (google_sso settings) ----

    /** Parse the stored google_sso JSONB; null/blank/corrupt → disabled defaults. */
    public static GoogleSsoConfig fromJson(String json) {
        if (json == null || json.isBlank()) return new GoogleSsoConfig();
        try {
            return new ObjectMapper().readValue(json, GoogleSsoConfig.class);
        } catch (Exception e) {
            return new GoogleSsoConfig();
        }
    }

    public static String toJson(GoogleSsoConfig cfg) {
        try {
            return new ObjectMapper().writeValueAsString(cfg);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize SSO config", e);
        }
    }
}
