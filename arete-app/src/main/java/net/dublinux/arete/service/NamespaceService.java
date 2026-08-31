package net.dublinux.arete.service;

import net.dublinux.arete.domain.NamespaceEntity;
import net.dublinux.arete.repository.NamespaceRepository;
import net.dublinux.arete.repository.SpecRepository;
import net.dublinux.arete.web.api.Slugs;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Owns the {@code namespaces} table. Namespaces keep the casing the creator
 * typed ({@code name}); uniqueness and every spec lookup key off the
 * lower-cased {@code key}. A namespace can exist with zero specs (created from
 * Settings); one is also created implicitly the first time a spec is saved
 * under a not-yet-seen key.
 */
@Service
public class NamespaceService {

    /** The always-present namespace pre-existing specs fall into. */
    public static final String DEFAULT_KEY = "default";

    private final NamespaceRepository repository;
    private final SpecRepository specRepository;

    public NamespaceService(NamespaceRepository repository, SpecRepository specRepository) {
        this.repository = repository;
        this.specRepository = specRepository;
    }

    /** The lower-cased lookup key for a user-typed name, or null if nothing usable remains. */
    public static String keyOf(String name) {
        return Slugs.slugify(name);
    }

    public record Namespace(String name, String key, long specCount) { }

    /**
     * Resolves a user-supplied name to an existing namespace, creating it if
     * new. Returns the default namespace when {@code name} is null/blank/unusable.
     */
    @Transactional
    public NamespaceEntity resolveOrCreate(String name) {
        String trimmed = name == null ? "" : name.trim();
        String key = keyOf(trimmed);
        if (key == null) {
            return ensureDefault();
        }
        return repository.findByNameKey(key)
                .orElseGet(() -> repository.save(new NamespaceEntity(trimmed, key)));
    }

    /** Resolves an existing key to its entity, else the default namespace. */
    @Transactional
    public NamespaceEntity resolveKey(String key) {
        if (key == null) {
            return ensureDefault();
        }
        return repository.findByNameKey(key.toLowerCase()).orElseGet(this::ensureDefault);
    }

    public Optional<NamespaceEntity> findByKey(String key) {
        return key == null ? Optional.empty() : repository.findByNameKey(key.toLowerCase());
    }

    @Transactional
    public NamespaceEntity ensureDefault() {
        return repository.findByNameKey(DEFAULT_KEY)
                .orElseGet(() -> repository.save(new NamespaceEntity("default", DEFAULT_KEY)));
    }

    /** Every namespace with its spec count, name order. Backfills rows for any key seen only in specs. */
    @Transactional
    public List<Namespace> list() {
        ensureDefault();
        for (String key : specRepository.findDistinctNamespaces()) {
            if (key != null && !repository.existsByNameKey(key)) {
                repository.save(new NamespaceEntity(key, key));
            }
        }
        return repository.findAllByOrderByNameAsc().stream()
                .map(n -> new Namespace(n.getName(), n.getNameKey(), specRepository.countByNamespace(n.getNameKey())))
                .toList();
    }

    /** Creates a namespace from Settings. Returns false if the name is unusable or the key already exists. */
    @Transactional
    public boolean create(String name) {
        String trimmed = name == null ? "" : name.trim();
        String key = keyOf(trimmed);
        if (key == null || repository.existsByNameKey(key)) {
            return false;
        }
        repository.save(new NamespaceEntity(trimmed, key));
        return true;
    }

    /** Deletes a namespace, only if it holds no specs and isn't the default. */
    @Transactional
    public boolean deleteIfEmpty(String key) {
        if (key == null || DEFAULT_KEY.equals(key) || specRepository.countByNamespace(key) > 0) {
            return false;
        }
        repository.findByNameKey(key).ifPresent(repository::delete);
        return true;
    }
}
