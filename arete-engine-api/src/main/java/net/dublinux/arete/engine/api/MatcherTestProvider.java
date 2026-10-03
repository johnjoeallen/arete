package net.dublinux.arete.engine.api;

/** Optional plugin capability for the developer matcher workbench. */
public interface MatcherTestProvider {
    ScoringResult testMatcher(MatcherTestRequest request);
}
