package net.dublinux.arete.validation.spi;

/** Optional plugin capability for the developer matcher workbench. */
public interface MatcherTestProvider {
    ValidationResult testMatcher(MatcherTestRequest request);
}
