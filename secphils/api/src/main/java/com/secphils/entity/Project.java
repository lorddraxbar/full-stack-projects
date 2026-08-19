package com.secphils.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "projects")
@Getter
@Setter
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id")
    private Service service;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String scope;

    @Column(columnDefinition = "TEXT")
    private String objectives;

    @Column(columnDefinition = "TEXT")
    private String deliverables;

    @Column(length = 30)
    private String status = "NOT_STARTED";

    @Column(name = "total_cost", precision = 15, scale = 2)
    private BigDecimal totalCost;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_materials", columnDefinition = "JSONB")
    private String rawMaterials;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "production_output", columnDefinition = "JSONB")
    private String productionOutput;

    @Column(name = "waste_management", columnDefinition = "TEXT")
    private String wasteManagement;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "waste_materials", columnDefinition = "JSONB")
    private String wasteMaterials;

    @Column(name = "manufacturing_procedure", columnDefinition = "TEXT")
    private String manufacturingProcedure;

    @Column(name = "production_flowchart_url", length = 500)
    private String productionFlowchartUrl;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Task> tasks = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Document> documents = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Message> messages = new ArrayList<>();

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
