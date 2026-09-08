package com.secphils.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * SMTP relay configuration, stored as JSONB on system_settings (V34).
 * Overrides the env-configured sender at runtime (live after save, no
 * restart). The password is masked to {@link #SECRET_MASK} on admin reads;
 * saving the mask (or blank) keeps the stored password.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class SmtpConfig {
    public static final String SECRET_MASK = "********";

    public String host = "smtp.zoho.com";
    public int port = 465;
    public String username = "";
    public String password = "";
    /** Default From: header — e.g. notifications@secphils.com. */
    public String from = "";

    public boolean isConfigured() {
        return host != null && !host.isBlank() && username != null && !username.isBlank();
    }

    /** Masked copy for admin read responses. */
    public static SmtpConfig masked(SmtpConfig src) {
        if (src == null) return null;
        SmtpConfig m = new SmtpConfig();
        m.host = src.host;
        m.port = src.port;
        m.username = src.username;
        m.password = src.password != null && !src.password.isBlank() ? SECRET_MASK : "";
        m.from = src.from;
        return m;
    }
}
