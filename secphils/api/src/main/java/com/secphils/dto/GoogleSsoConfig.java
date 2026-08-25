package com.secphils.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Google OAuth 2.0 configuration, stored as JSONB on system_settings.
 * The client secret is masked to {@link #SECRET_MASK} on read by the admin
 * settings endpoint; saving the mask (or blank) keeps the stored secret.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class GoogleSsoConfig {
    public static final String SECRET_MASK = "********";

    public boolean enabled = false;
    public String clientId = "";
    public String clientSecret = "";
    public String redirectUri = "";
    public String domainRestriction = ""; // optional: only allow @domain emails

    /** Masked copy for admin read responses. */
    public static GoogleSsoConfig masked(GoogleSsoConfig src) {
        if (src == null) return null;
        GoogleSsoConfig m = new GoogleSsoConfig();
        m.enabled = src.enabled;
        m.clientId = src.clientId;
        m.clientSecret = src.clientSecret != null && !src.clientSecret.isBlank()
                ? SECRET_MASK : "";
        m.redirectUri = src.redirectUri;
        m.domainRestriction = src.domainRestriction;
        return m;
    }
}
