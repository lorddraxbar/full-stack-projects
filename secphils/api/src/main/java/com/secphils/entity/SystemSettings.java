package com.secphils.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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

    @Column(name = "email_templates", columnDefinition = "JSONB")
    private String emailTemplates;

    @Column(columnDefinition = "JSONB")
    private String integrations;

    @Column(name = "security_policies", columnDefinition = "JSONB")
    private String securityPolicies;

    @Column(name = "maintenance_mode")
    private Boolean maintenanceMode = false;

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
