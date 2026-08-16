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

    /**
     * The rule-set picker's last-submitted position (see {@code
     * SpecController#resolveRuleSet}) for this plugin on this spec; {@code
     * null} means "never chosen, default to index 0" — same convention as
     * an absent row entirely.
     */
    @Column(name = "rule_set_index")
    private Integer ruleSetIndex;

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

    public Integer getRuleSetIndex() {
        return ruleSetIndex;
    }

    public void setRuleSetIndex(Integer ruleSetIndex) {
        this.ruleSetIndex = ruleSetIndex;
    }

}
