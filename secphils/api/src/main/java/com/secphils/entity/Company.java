package com.secphils.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "companies")
@Getter
@Setter
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(length = 255)
    private String location;

    @Column(length = 255)
    private String owner;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 255)
    private String tagline;

    @Column(name = "industry_sectors", length = 500)
    private String industrySectors;

    @Column(length = 500)
    private String headquarters;

    @Column(length = 100)
    private String phone;

    @Column(length = 255)
    private String email;

    @Column(length = 255)
    private String website;

    @Column(name = "social_links", length = 500)
    private String socialLinks;

    @Column(name = "tax_number", length = 100)
    private String taxNumber;

    @Column(name = "banking_details", columnDefinition = "TEXT")
    private String bankingDetails;

    @Column(name = "operational_fields", length = 500)
    private String operationalFields;

    @Column(name = "brand_primary", length = 7)
    private String brandPrimary;

    @Column(name = "brand_secondary", length = 7)
    private String brandSecondary;

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "authorized_rep_user_id")
    private User authorizedRep;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
