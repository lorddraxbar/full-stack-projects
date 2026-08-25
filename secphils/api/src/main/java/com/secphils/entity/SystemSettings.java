package com.secphils.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_settings")
@Getter
@Setter
public class SystemSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "portal_name", length = 255)
    private String portalName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "email_templates", columnDefinition = "JSONB")
    private String emailTemplates;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB")
    private String integrations;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSONB")
    private String storage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "google_sso", columnDefinition = "JSONB")
    private String googleSso;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "security_policies", columnDefinition = "JSONB")
    private String securityPolicies;

    @Column(name = "maintenance_mode")
    private Boolean maintenanceMode = false;

    @Column(name = "invite_base_url")
    private String inviteBaseUrl;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
