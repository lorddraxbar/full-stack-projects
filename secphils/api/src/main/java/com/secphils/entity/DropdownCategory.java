package com.secphils.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "dropdown_categories")
@Getter
@Setter
public class DropdownCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /**
     * Protected categories are structural vocabulary the auth/system layer
     * depends on (user_role) or which the backend maps to lifecycle behavior
     * (project_status -> ARCHIVED). V35; writes are refused by
     * DropdownController.requireEditable(). Column name "protected" is a
     * Java keyword clash -> field is protectedFlag.
     */
    @Column(name = "is_protected", nullable = false)
    private Boolean protectedFlag = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<DropdownValue> values = new ArrayList<>();

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
