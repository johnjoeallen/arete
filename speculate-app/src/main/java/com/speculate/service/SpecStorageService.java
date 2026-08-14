package com.speculate.service;

import com.speculate.domain.SpecEntity;
import com.speculate.domain.SpecSource;
import com.speculate.repository.SpecRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SpecStorageService {

    private final SpecRepository repository;

    public SpecStorageService(SpecRepository repository) {
        this.repository = repository;
    }

    /** Saves pasted spec text. Clears any prior file association — a paste replaces a file link, it doesn't layer on it. */
    @Transactional
    public SpecEntity saveOrReplace(String title, String rawContent) {
        return saveOrReplace(title, rawContent, Map.of());
    }

    /** As {@link #saveOrReplace(String, String)}, additionally setting the per-plugin rule set selection made when the spec was added. */
    @Transactional
    public SpecEntity saveOrReplace(String title, String rawContent, Map<String, String> pluginRuleSets) {
        SpecEntity entity = repository.findByTitle(title).orElseGet(SpecEntity::new);
        entity.setTitle(title);
        entity.setRawContent(rawContent);
        entity.setUpdatedAt(Instant.now());
        entity.setSource(SpecSource.PASTED);
        entity.setFilePath(null);
        entity.setContentHash(null);
        entity.setPluginRuleSets(pluginRuleSets);
        return repository.save(entity);
    }

    /**
     * Saves spec text loaded from a local file, recording the path and a
     * content hash so a later change to the file can be detected. Matches
     * {@code saveOrReplace}'s upsert-by-title behavior: dragging the same
     * file (same {@code info.title}) again overwrites in place rather than
     * duplicating.
     */
    @Transactional
    public SpecEntity saveOrReplaceFromFile(String title, String rawContent, String filePath) {
        return saveOrReplaceFromFile(title, rawContent, filePath, Map.of());
    }

    /** As {@link #saveOrReplaceFromFile(String, String, String)}, additionally setting the per-plugin rule set selection made when the spec was added. */
    @Transactional
    public SpecEntity saveOrReplaceFromFile(String title, String rawContent, String filePath, Map<String, String> pluginRuleSets) {
        SpecEntity entity = repository.findByTitle(title).orElseGet(SpecEntity::new);
        entity.setTitle(title);
        entity.setRawContent(rawContent);
        entity.setUpdatedAt(Instant.now());
        entity.setSource(SpecSource.FILE);
        entity.setFilePath(filePath);
        entity.setContentHash(sha256Hex(rawContent));
        entity.setPluginRuleSets(pluginRuleSets);
        return repository.save(entity);
    }

    /**
     * Upserts spec content watched from a file, keyed by {@code filePath}
     * rather than title. Used by the file watcher, where the same file is
     * being re-read repeatedly and must keep updating the same row even if
     * the spec's {@code info.title} changes between reads — unlike the
     * title-keyed {@link #saveOrReplaceFromFile}, which a rename would
     * orphan into a second row. Falls back to the title-keyed upsert the
     * first time a given path is seen, since there's no row to key off yet.
     * A no-op (no write) when the content hash is unchanged, so filesystem
     * notifications that don't actually change bytes don't bump updatedAt.
     */
    @Transactional
    public SpecEntity saveOrUpdateFromFile(String title, String rawContent, String filePath) {
        Optional<SpecEntity> existing = repository.findByFilePath(filePath);
        if (existing.isEmpty()) {
            return saveOrReplaceFromFile(title, rawContent, filePath);
        }
        String hash = sha256Hex(rawContent);
        SpecEntity entity = existing.get();
        if (hash.equals(entity.getContentHash())) {
            return entity;
        }
        entity.setTitle(title);
        entity.setRawContent(rawContent);
        entity.setUpdatedAt(Instant.now());
        entity.setContentHash(hash);
        return repository.save(entity);
    }

    public List<SpecEntity> findAll() {
        return repository.findAll();
    }

    public Optional<SpecEntity> findById(Long id) {
        return repository.findById(id);
    }

    @Transactional
    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    private static String sha256Hex(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a guaranteed JDK algorithm (JLS platform requirement); this can't happen.
            throw new IllegalStateException(e);
        }
    }

}
