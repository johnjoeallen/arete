package net.dublinux.arete.plugin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists, per spec, whether the engine is selected and which policy was
 * chosen. A (specId, pluginId) pair with no row defaults to selected.
 */
@Service
public class SpecPluginSettingsService {

    private final SpecPluginSettingsRepository repository;

    public SpecPluginSettingsService(SpecPluginSettingsRepository repository) {
        this.repository = repository;
    }

    /** Defaults to {@code true} when no override row exists for this spec/plugin pair. */
    public boolean isEnabledForSpec(Long specId, String pluginId) {
        return repository.findBySpecIdAndPluginId(specId, pluginId)
                .map(SpecPluginSettingsEntity::isEnabled)
                .orElse(true);
    }

    /** {@code null} — "never chosen, default to index 0" — when no override row exists. */
    public Integer policyIndexForSpec(Long specId, String pluginId) {
        return repository.findBySpecIdAndPluginId(specId, pluginId)
                .map(SpecPluginSettingsEntity::getPolicyIndex)
                .orElse(null);
    }

    @Transactional
    public void setSelection(Long specId, String pluginId, boolean enabled, Integer policyIndex) {
        SpecPluginSettingsEntity entity = repository.findBySpecIdAndPluginId(specId, pluginId)
                .orElseGet(() -> {
                    SpecPluginSettingsEntity created = new SpecPluginSettingsEntity();
                    created.setSpecId(specId);
                    created.setPluginId(pluginId);
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
