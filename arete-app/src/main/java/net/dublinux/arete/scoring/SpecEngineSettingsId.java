package net.dublinux.arete.scoring;

import java.io.Serializable;
import java.util.Objects;

/** {@link jakarta.persistence.IdClass} companion for {@link SpecEngineSettingsEntity}'s composite key. */
public class SpecEngineSettingsId implements Serializable {

    private Long specId;
    private String engineId;

    public SpecEngineSettingsId() {
    }

    public SpecEngineSettingsId(Long specId, String engineId) {
        this.specId = specId;
        this.engineId = engineId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SpecEngineSettingsId other)) {
            return false;
        }
        return Objects.equals(specId, other.specId) && Objects.equals(engineId, other.engineId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(specId, engineId);
    }
}
