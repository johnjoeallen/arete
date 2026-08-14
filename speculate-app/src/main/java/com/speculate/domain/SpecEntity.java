package com.speculate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "specs", uniqueConstraints = @UniqueConstraint(columnNames = "title"))
public class SpecEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String title;

    @Lob
    @Column(nullable = false)
    private String rawContent;

    @Column(nullable = false)
    private Instant updatedAt;

    // Existing rows predate this column; the inline default lets Hibernate's
    // schema update add it NOT NULL to a non-empty table without failing, and
    // backfills every pre-existing row as PASTED (they were, by definition).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20) default 'PASTED'")
    private SpecSource source = SpecSource.PASTED;

    /** Absolute path of the source file; null for a {@link SpecSource#PASTED} spec. */
    @Column(name = "file_path")
    private String filePath;

    /**
     * Hex-encoded SHA-256 of {@link #rawContent} as last saved, used to
     * confirm a filesystem change-notification actually changed the bytes
     * (see {@link SpecSource#FILE}); null for a {@link SpecSource#PASTED} spec.
     */
    @Column(name = "content_hash")
    private String contentHash;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getRawContent() {
        return rawContent;
    }

    public void setRawContent(String rawContent) {
        this.rawContent = rawContent;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public SpecSource getSource() {
        return source;
    }

    public void setSource(SpecSource source) {
        this.source = source;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

}
