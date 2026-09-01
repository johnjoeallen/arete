package net.dublinux.arete.scoring.spi;

/** Optional plugin capability for the developer matcher workbench. */
public interface MatcherTestProvider {
    ScoringResult testMatcher(MatcherTestRequest request);
}
