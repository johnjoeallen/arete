package net.dublinux.arete.service;

import net.dublinux.arete.domain.SpecEntity;
import net.dublinux.arete.domain.SpecSource;
import net.dublinux.arete.repository.SpecRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class SpecStorageService {

    /** Namespace / submitter used by callers that predate the concept (the browser paste form, the file watcher). */
    public static final String DEFAULT_NAMESPACE = "default";
    public static final String UI_SUBMITTER = "ui";

    private final SpecRepository repository;

    public SpecStorageService(SpecRepository repository) {
        this.repository = repository;
    }

    // --- pasted / API-inline text -------------------------------------------

    /** Backwards-compatible facade: the {@value #DEFAULT_NAMESPACE} namespace, submitted by {@value #UI_SUBMITTER}. */
    @Transactional
    public SpecEntity saveOrReplace(String title, String rawContent) {
        return saveOrReplace(DEFAULT_NAMESPACE, UI_SUBMITTER, title, rawContent);
    }

    /** Upserts pasted spec text keyed by {@code (namespace, title)}. Clears any prior file/URL association. */
    @Transactional
    public SpecEntity saveOrReplace(String namespace, String submitter, String title, String rawContent) {
        SpecEntity entity = upsertTarget(namespace, title);
        entity.setNamespace(namespace);
        entity.setSubmitter(submitter);
        entity.setTitle(title);
        entity.setRawContent(rawContent);
        entity.setUpdatedAt(Instant.now());
        entity.setSource(SpecSource.PASTED);
        entity.setFilePath(null);
        entity.setSourceUrl(null);
        entity.setContentHash(null);
        return repository.save(entity);
    }

    // --- fetched from a URL ------------------------------------------------

    /** Upserts spec text fetched from {@code sourceUrl}, keyed by {@code (namespace, title)}. */
    @Transactional
    public SpecEntity saveOrReplaceFromUrl(String namespace, String submitter, String title, String rawContent, String sourceUrl) {
        SpecEntity entity = upsertTarget(namespace, title);
        entity.setNamespace(namespace);
        entity.setSubmitter(submitter);
        entity.setTitle(title);
        entity.setRawContent(rawContent);
        entity.setUpdatedAt(Instant.now());
        entity.setSource(SpecSource.URL);
        entity.setFilePath(null);
        entity.setSourceUrl(sourceUrl);
        entity.setContentHash(sha256Hex(rawContent));
        return repository.save(entity);
    }

    // --- loaded from a local file ---------------------------------------------

    /** Backwards-compatible facade: the {@value #DEFAULT_NAMESPACE} namespace. */
    @Transactional
    public SpecEntity saveOrReplaceFromFile(String title, String rawContent, String filePath) {
        SpecEntity entity = upsertTarget(DEFAULT_NAMESPACE, title);
        entity.setNamespace(DEFAULT_NAMESPACE);
        entity.setSubmitter(UI_SUBMITTER);
        entity.setTitle(title);
        entity.setRawContent(rawContent);
        entity.setUpdatedAt(Instant.now());
        entity.setSource(SpecSource.FILE);
        entity.setFilePath(filePath);
        entity.setSourceUrl(null);
        entity.setContentHash(sha256Hex(rawContent));
        return repository.save(entity);
    }

    /**
     * Upserts spec content watched from a file, keyed by {@code filePath}
     * rather than title. Used by the file watcher, where the same file is
     * being re-read repeatedly and must keep updating the same row even if
     * the spec's {@code info.title} changes between reads. Falls back to the
     * title-keyed upsert the first time a given path is seen. A no-op (no
     * write) when the content hash is unchanged.
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

    // --- reads -------------------------------------------------------------

    public List<SpecEntity> findAll() {
        return repository.findAll();
    }

    public List<SpecEntity> findByNamespace(String namespace) {
        return repository.findByNamespaceOrderByTitleAsc(namespace);
    }

    public List<SpecEntity> findByNamespaceAndSubmitter(String namespace, String submitter) {
        return repository.findByNamespaceAndSubmitterOrderByTitleAsc(namespace, submitter);
    }

    public List<String> namespaces() {
        return repository.findDistinctNamespaces();
    }

    public long countInNamespace(String namespace) {
        return repository.countByNamespace(namespace);
    }

    public Optional<SpecEntity> findById(Long id) {
        return repository.findById(id);
    }

    public Optional<SpecEntity> findByIdInNamespace(Long id, String namespace) {
        return repository.findByIdAndNamespace(id, namespace);
    }

    @Transactional
    public void deleteById(Long id) {
        repository.deleteById(id);
    }

    // --- internals -------------------------------------------------------

    private SpecEntity upsertTarget(String namespace, String title) {
        return repository.findByNamespaceAndTitle(namespace, title).orElseGet(SpecEntity::new);
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
