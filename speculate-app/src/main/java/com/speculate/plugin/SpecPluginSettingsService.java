package com.speculate.plugin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a per-spec override of a validator plugin's enabled/disabled
 * state. A (specId, pluginId) pair the host has never seen an override for
 * defaults to enabled, mirroring {@link PluginSettingsService}'s own
 * default — so a plugin that's on globally is also on for every spec until
 * someone explicitly unchecks it there.
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
    public Integer ruleSetIndexForSpec(Long specId, String pluginId) {
        return repository.findBySpecIdAndPluginId(specId, pluginId)
                .map(SpecPluginSettingsEntity::getRuleSetIndex)
                .orElse(null);
    }

    @Transactional
    public void setSelection(Long specId, String pluginId, boolean enabled, Integer ruleSetIndex) {
        SpecPluginSettingsEntity entity = repository.findBySpecIdAndPluginId(specId, pluginId)
                .orElseGet(() -> {
                    SpecPluginSettingsEntity created = new SpecPluginSettingsEntity();
                    created.setSpecId(specId);
                    created.setPluginId(pluginId);
                    return created;
                });
        entity.setEnabled(enabled);
        entity.setRuleSetIndex(ruleSetIndex);
        repository.save(entity);
    }

    @Transactional
    public void deleteAllForSpec(Long specId) {
        repository.deleteBySpecId(specId);
    }

}
