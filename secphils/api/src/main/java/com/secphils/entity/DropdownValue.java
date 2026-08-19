package com.secphils.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "dropdown_values",
       uniqueConstraints = @UniqueConstraint(columnNames = {"category_id", "value"}))
@Getter
@Setter
public class DropdownValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private DropdownCategory category;

    @Column(nullable = false, length = 100)
    private String value;

    @Column(name = "display_label", nullable = false, length = 100)
    private String displayLabel;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
