package net.dublinux.arete.plugin;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Persists and reloads the most recent Score run's result per spec — see
 * {@link SpecScoringResultEntity}.
 */
@Service
public class SpecScoringResultService {

    private final SpecScoringResultRepository repository;
    // Not the Spring-autoconfigured bean (this app has no guarantee one is
    // registered — it depends on MVC's Jackson auto-config, not something
    // this persistence concern should couple to); same "just new one up"
    // approach ExampleRenderer already uses for its own JSON needs.
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SpecScoringResultService(SpecScoringResultRepository repository) {
        this.repository = repository;
    }

    /** SHA-256 of a spec's raw content, for both storing alongside a run and comparing against later. */
    public static String contentHashOf(String rawContent) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawContent.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a guaranteed JDK algorithm (JLS platform requirement); this can't happen.
            throw new IllegalStateException(e);
        }
    }

    @Transactional
    public void save(Long specId, String contentHash, AggregatedScoringResult result, List<String> activePluginIds) {
        SpecScoringResultEntity entity = repository.findById(specId).orElseGet(() -> {
            SpecScoringResultEntity created = new SpecScoringResultEntity();
            created.setSpecId(specId);
            return created;
        });
        entity.setContentHash(contentHash);
        entity.setResultJson(toJson(result, activePluginIds));
        entity.setRanAt(Instant.now());
        repository.save(entity);
    }

    /** Empty if this spec has never been analysed. */
    public Optional<CachedScoringResult> findForSpec(Long specId) {
        return repository.findById(specId).map(entity -> fromJson(entity.getResultJson()));
    }

    @Transactional
    public void deleteForSpec(Long specId) {
        repository.deleteBySpecId(specId);
    }

    private String toJson(AggregatedScoringResult result, List<String> activePluginIds) {
        try {
            return ScoringResultSnapshotCodec.toJson(objectMapper, result, activePluginIds);
        } catch (JsonProcessingException e) {
            // Our own DTOs, built from data this same JVM just produced — a
            // failure here means a real bug in the codec, not bad input.
            throw new IllegalStateException("Failed to serialize a scoring result", e);
        }
    }

    private CachedScoringResult fromJson(String json) {
        try {
            return ScoringResultSnapshotCodec.fromJson(objectMapper, json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialize a stored scoring result", e);
        }
    }

}
