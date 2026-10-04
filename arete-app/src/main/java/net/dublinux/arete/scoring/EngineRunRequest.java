package net.dublinux.arete.scoring;

/**
 * One engine to run, with the policy to run it under. {@code policy}
 * follows {@link net.dublinux.arete.engine.api.Engine#DEFAULT_POLICY}'s
 * own null/blank-means-default convention — see {@link EngineScoringService#scoreMany}.
 */
public record EngineRunRequest(String engineId, String policy) {
}
