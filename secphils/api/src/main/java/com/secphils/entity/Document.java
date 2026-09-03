package com.secphils.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Entity
@Table(name = "documents")
@Getter
@Setter
public class Document {

    private static final List<String> IMAGE_EXTS = List.of("png", "jpg", "jpeg", "gif", "webp", "svg", "bmp", "tiff");
    private static final List<String> SPREADSHEET_EXTS = List.of("xls", "xlsx", "csv", "ods");
    private static final List<String> ARCHIVE_EXTS = List.of("zip", "tar", "gz", "tgz", "7z", "rar");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploader_id")
    private User uploader;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "file_url", nullable = false, length = 1000)
    private String fileUrl;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(nullable = false)
    private Integer version = 1;

    @Column(name = "is_latest")
    private Boolean isLatest = true;

    @Column(name = "uploaded_at", updatable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Trash: set when the document is "deleted"; null = live. */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** Who moved the document to the trash. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deleted_by_id")
    private User deletedBy;

    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<DocumentComment> comments = new ArrayList<>();

    /**
     * File format, derived from the stored file name (the s3:// object key keeps the
     * original name, e.g. {@code .../{uuid}__report.pdf}). One of IMAGE, PDF, WORD,
     * SPREADSHEET, PRESENTATION, ARCHIVE, OTHER. Metadata-only documents (no file yet)
     * are OTHER.
     */
    public String fileType() {
        String name = fileName();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "OTHER";
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (IMAGE_EXTS.contains(ext)) return "IMAGE";
        if (ext.equals("pdf")) return "PDF";
        if (ext.equals("doc") || ext.equals("docx") || ext.equals("odt") || ext.equals("rtf")) return "WORD";
        if (SPREADSHEET_EXTS.contains(ext)) return "SPREADSHEET";
        if (ext.equals("ppt") || ext.equals("pptx") || ext.equals("odp")) return "PRESENTATION";
        if (ARCHIVE_EXTS.contains(ext)) return "ARCHIVE";
        return "OTHER";
    }

    /** Last path segment of the file reference (works for both s3:// and http(s) URLs). */
    public String fileName() {
        if (fileUrl == null || fileUrl.isBlank()) return "";
        String s = fileUrl;
        int q = s.indexOf('?');
        if (q >= 0) s = s.substring(0, q);
        int slash = s.lastIndexOf('/');
        return slash >= 0 ? s.substring(slash + 1) : s;
    }

    @PrePersist
    void onCreate() {
        if (uploadedAt == null) uploadedAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
