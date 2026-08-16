package com.speculate.plugin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * The most recent Analyse run's result for a spec, so reopening it shows
 * those findings again instead of an empty picker — validation is still
 * on-demand (nothing runs automatically), but the last thing it computed
 * doesn't vanish the moment the page is left. One row per spec; a new run
 * overwrites rather than appending, there's no history kept.
 *
 * <p>{@code contentHash} is a SHA-256 of the spec's {@code rawContent} at
 * run time (independent of {@link com.speculate.domain.SpecEntity#getContentHash()},
 * which is null for a pasted spec — this needs to work for every spec,
 * not just file-sourced ones) — the host compares it against the spec's
 * current content to decide whether this cached result is still current,
 * i.e. whether the Analyse button should start enabled.
 */
@Entity
@Table(name = "spec_validation_results")
public class SpecValidationResultEntity {

    @Id
    @Column(name = "spec_id", nullable = false)
    private Long specId;

    @Column(name = "content_hash", nullable = false)
    private String contentHash;

    @Lob
    @Column(name = "result_json", nullable = false)
    private String resultJson;

    @Column(name = "ran_at", nullable = false)
    private Instant ranAt;

    public Long getSpecId() {
        return specId;
    }

    public void setSpecId(Long specId) {
        this.specId = specId;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public Instant getRanAt() {
        return ranAt;
    }

    public void setRanAt(Instant ranAt) {
        this.ranAt = ranAt;
    }

}
