package net.dublinux.arete.plugin;

import java.io.Serializable;
import java.util.Objects;

/** {@link jakarta.persistence.IdClass} companion for {@link SpecPluginSettingsEntity}'s composite key. */
public class SpecPluginSettingsId implements Serializable {

    private Long specId;
    private String pluginId;

    public SpecPluginSettingsId() {
    }

    public SpecPluginSettingsId(Long specId, String pluginId) {
        this.specId = specId;
        this.pluginId = pluginId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SpecPluginSettingsId other)) {
            return false;
        }
        return Objects.equals(specId, other.specId) && Objects.equals(pluginId, other.pluginId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(specId, pluginId);
    }
}
