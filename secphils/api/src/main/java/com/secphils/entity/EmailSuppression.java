package com.secphils.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * An address we must not email ({@code category} '' = the whole address,
 * e.g. a hard bounce or spam complaint). Category-granular rows are
 * representable but currently unwritten — unsubscribes live in
 * notification_preferences instead (see {@code EmailSuppressionService}).
 * See V40. Checked by {@code MailService} before every send.
 */
@Entity
@Table(name = "email_suppressions")
@Getter
@Setter
public class EmailSuppression {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 64)
    private String category = "";

    /** unsubscribe | bounce | complaint */
    @Column(nullable = false, length = 32)
    private String reason;

    @Column(length = 500)
    private String detail;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
