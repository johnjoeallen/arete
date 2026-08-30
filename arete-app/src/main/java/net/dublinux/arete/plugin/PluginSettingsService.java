package net.dublinux.arete.plugin;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * Persists the enabled/disabled flag for each known validator plugin ID. A
 * plugin ID the host has never seen before defaults to enabled.
 */
@Service
public class PluginSettingsService {

    private final PluginSettingsRepository repository;

    public PluginSettingsService(PluginSettingsRepository repository) {
        this.repository = repository;
    }

    /** Inserts an enabled=true row for any of the given IDs that isn't already persisted. */
    @Transactional
    public void ensureDefaults(Collection<String> pluginIds) {
        for (String pluginId : pluginIds) {
            if (repository.findById(pluginId).isEmpty()) {
                PluginSettingsEntity entity = new PluginSettingsEntity();
                entity.setPluginId(pluginId);
                entity.setEnabled(true);
                repository.save(entity);
            }
        }
    }

    /** Defaults to {@code true} for a plugin ID with no persisted row. */
    public boolean isEnabled(String pluginId) {
        return repository.findById(pluginId).map(PluginSettingsEntity::isEnabled).orElse(true);
    }

    @Transactional
    public void setEnabled(String pluginId, boolean enabled) {
        PluginSettingsEntity entity = repository.findById(pluginId).orElseGet(() -> {
            PluginSettingsEntity created = new PluginSettingsEntity();
            created.setPluginId(pluginId);
            return created;
        });
        entity.setEnabled(enabled);
        repository.save(entity);
    }

    public List<PluginSettingsEntity> findAll() {
        return repository.findAll();
    }

}
