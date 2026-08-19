package com.secphils.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification_preferences")
@Getter
@Setter
public class NotificationPreference {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "task_assigned")
    private Boolean taskAssigned = true;

    @Column(name = "project_created")
    private Boolean projectCreated = true;

    @Column(name = "new_message")
    private Boolean newMessage = true;

    @Column(name = "document_request")
    private Boolean documentRequest = true;

    @Column(name = "review_submitted")
    private Boolean reviewSubmitted = true;

    @Column(name = "announcement")
    private Boolean announcement = true;

    @Column(name = "status_change")
    private Boolean statusChange = true;

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
