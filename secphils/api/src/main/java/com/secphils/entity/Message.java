package com.secphils.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "messages")
@Getter
@Setter
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** Optional file attachment: an s3://bucket/key ref (or http(s) URL) in object storage. */
    @Column(name = "attachment_url")
    private String attachmentUrl;

    @Column(name = "attachment_file_name")
    private String attachmentFileName;

    @Column(name = "attachment_file_size")
    private Long attachmentFileSize;

    @Column(name = "attachment_content_type")
    private String attachmentContentType;

    /** 'CLIENT' (default, visible to company members + admin) or 'INTERNAL' (provider staff only). */
    @Column(nullable = false, length = 20)
    private String visibility = "CLIENT";

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
