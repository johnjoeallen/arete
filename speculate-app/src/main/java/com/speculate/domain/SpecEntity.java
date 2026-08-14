package com.speculate.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

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

    /**
     * Which {@link net.dublinux.speculate.validation.spi.SpecValidationPlugin#getValidationTypes()}
     * entry to use for this spec, keyed by plugin ID. Chosen once when the
     * spec is added and reused on every later validation run (including
     * every time the spec is reopened) so the choice doesn't have to be
     * re-made; a plugin with no entry here falls back to {@code
     * SpecValidationPlugin#DEFAULT_VALIDATION_TYPE}.
     *
     * <p>Eager, not the JPA default lazy: this is a tiny map that's always
     * needed alongside the entity (every validate() call reads it), and the
     * host reads it well outside the loading transaction/session (see
     * SpecController#open) where a lazy collection can't be initialized.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "spec_plugin_validation_types", joinColumns = @JoinColumn(name = "spec_id"))
    @MapKeyColumn(name = "plugin_id")
    @Column(name = "validation_type")
    private Map<String, String> pluginValidationTypes = new HashMap<>();

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

    /** Never null; empty means every plugin uses its own default validation type. */
    public Map<String, String> getPluginValidationTypes() {
        return pluginValidationTypes;
    }

    public void setPluginValidationTypes(Map<String, String> pluginValidationTypes) {
        this.pluginValidationTypes = pluginValidationTypes == null ? new HashMap<>() : new HashMap<>(pluginValidationTypes);
    }

}
