package net.dublinux.arete.scoring;

import com.fasterxml.jackson.annotation.JsonAlias;

/** JSON-serializable mirror of {@link ScoringSummary}. */
public record PersistedScoringSummary(@JsonAlias("pluginName") String engineName, String status, int diagnosticCount, String errorMessage) {
}
