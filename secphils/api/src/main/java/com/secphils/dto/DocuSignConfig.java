package com.secphils.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DocuSign JWT (JWT grant) application credentials, stored as JSONB on
 * system_settings (V34). The private key is masked to {@link #SECRET_MASK}
 * on admin reads; saving the mask (or blank) keeps the stored key.
 * No feature ships on this yet — it is configuration + a live connection
 * test so credentials can be staged before the e-signature flow is built.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocuSignConfig {
    public static final String SECRET_MASK = "********";

    public boolean enabled = false;
    /** 'account-docusign.com' (production) or 'account-docusign-int.com' (sandbox). */
    public String oauthBaseUrl = "https://account-docusign.com";
    /** JWT OAuth client id (Integration Key). */
    public String integrationKey = "";
    public String accountId = "";
    /** Sub (user guid) the JWT impersonates. */
    public String userId = "";
    /** RSA private key pasted from the DocuSign app (PEM). */
    public String privateKey = "";

    public boolean isConfigured() {
        return enabled
                && integrationKey != null && !integrationKey.isBlank()
                && accountId != null && !accountId.isBlank()
                && userId != null && !userId.isBlank()
                && privateKey != null && !privateKey.isBlank();
    }

    /** Masked copy for admin read responses. */
    public static DocuSignConfig masked(DocuSignConfig src) {
        if (src == null) return null;
        DocuSignConfig m = new DocuSignConfig();
        m.enabled = src.enabled;
        m.oauthBaseUrl = src.oauthBaseUrl;
        m.integrationKey = src.integrationKey;
        m.accountId = src.accountId;
        m.userId = src.userId;
        m.privateKey = src.privateKey != null && !src.privateKey.isBlank() ? SECRET_MASK : "";
        return m;
    }
}
