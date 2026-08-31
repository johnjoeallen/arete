package net.dublinux.arete.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "specs")
public class SpecEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The namespace this spec belongs to — a plain caller-asserted slug, not a
     * security boundary. Uniqueness is {@code (namespace, title)}, enforced by
     * {@code SpecSchemaMigration} rather than a {@code @UniqueConstraint} so
     * the migration owns dropping the old title-only index.
     */
    @Column(nullable = false, columnDefinition = "varchar(64) default 'default'")
    private String namespace = "default";

    /** The self-declared submitter — a slug, never authenticated. */
    @Column(nullable = false, columnDefinition = "varchar(64) default 'anonymous'")
    private String submitter = "anonymous";

    @Column(nullable = false)
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

    /** Absolute path of the source file; null unless {@link #source} is {@link SpecSource#FILE}. */
    @Column(name = "file_path")
    private String filePath;

    /** The URL the spec was fetched from; null unless {@link #source} is {@link SpecSource#URL}. */
    @Column(name = "source_url")
    private String sourceUrl;

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

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getSubmitter() {
        return submitter;
    }

    public void setSubmitter(String submitter) {
        this.submitter = submitter;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
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
