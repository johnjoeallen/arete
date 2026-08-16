package com.speculate.plugin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/**
 * Per-spec override of a validator plugin's enabled/disabled state — lets
 * one spec run a narrower (or wider) set of plugins than the global default
 * in {@link PluginSettingsEntity}, e.g. only running an org-specific
 * "breaking changes" plugin against the APIs it's relevant to. Keyed by
 * ({@code specId}, {@code pluginId}); absence of a row means "no override,
 * defer to the global setting" — see {@link SpecPluginSettingsService}.
 */
@Entity
@Table(name = "spec_plugin_settings")
@IdClass(SpecPluginSettingsId.class)
public class SpecPluginSettingsEntity {

    @Id
    @Column(name = "spec_id", nullable = false)
    private Long specId;

    @Id
    @Column(name = "plugin_id", nullable = false)
    private String pluginId;

    @Column(nullable = false)
    private boolean enabled;

    public Long getSpecId() {
        return specId;
    }

    public void setSpecId(Long specId) {
        this.specId = specId;
    }

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
