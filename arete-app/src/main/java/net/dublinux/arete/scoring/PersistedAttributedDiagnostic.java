package net.dublinux.arete.scoring;

import com.fasterxml.jackson.annotation.JsonAlias;

/** JSON-serializable mirror of {@link AttributedDiagnostic}. */
public record PersistedAttributedDiagnostic(@JsonAlias("pluginId") String engineId, @JsonAlias("pluginName") String engineName, PersistedDiagnostic diagnostic) {
}
