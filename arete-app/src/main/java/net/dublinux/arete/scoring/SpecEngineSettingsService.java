package net.dublinux.arete.scoring;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists, per spec, whether the engine is selected and which policy was
 * chosen. A (specId, engineId) pair with no row defaults to selected.
 */
@Service
public class SpecEngineSettingsService {

    private final SpecEngineSettingsRepository repository;

    public SpecEngineSettingsService(SpecEngineSettingsRepository repository) {
        this.repository = repository;
    }

    /** Defaults to {@code true} when no override row exists for this spec/engine pair. */
    public boolean isEnabledForSpec(Long specId, String engineId) {
        return repository.findBySpecIdAndEngineId(specId, engineId)
                .map(SpecEngineSettingsEntity::isEnabled)
                .orElse(true);
    }

    /** {@code null} — "never chosen, default to index 0" — when no override row exists. */
    public Integer policyIndexForSpec(Long specId, String engineId) {
        return repository.findBySpecIdAndEngineId(specId, engineId)
                .map(SpecEngineSettingsEntity::getPolicyIndex)
                .orElse(null);
    }

    @Transactional
    public void setSelection(Long specId, String engineId, boolean enabled, Integer policyIndex) {
        SpecEngineSettingsEntity entity = repository.findBySpecIdAndEngineId(specId, engineId)
                .orElseGet(() -> {
                    SpecEngineSettingsEntity created = new SpecEngineSettingsEntity();
                    created.setSpecId(specId);
                    created.setEngineId(engineId);
                    return created;
                });
        entity.setEnabled(enabled);
        entity.setPolicyIndex(policyIndex);
        repository.save(entity);
    }

    @Transactional
    public void deleteAllForSpec(Long specId) {
        repository.deleteBySpecId(specId);
    }

}
