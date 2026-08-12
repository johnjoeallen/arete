package com.speculate.plugin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Persisted enabled/disabled flag for a validator plugin, keyed by
 * {@link speculate.validation.spi.SpecValidationPlugin#getId()} —
 * not by instance, since plugin instances don't survive a restart but IDs
 * do.
 */
@Entity
@Table(name = "plugin_settings")
public class PluginSettingsEntity {

    @Id
    @Column(name = "plugin_id", nullable = false)
    private String pluginId;

    @Column(nullable = false)
    private boolean enabled;

    public String getPluginId() {
        return pluginId;
    }

    public void setPluginId(String pluginId) {
        this.pluginId = pluginId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

}
