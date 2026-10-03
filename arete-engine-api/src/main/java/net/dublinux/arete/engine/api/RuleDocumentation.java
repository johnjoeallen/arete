package net.dublinux.arete.engine.api;

import java.util.Objects;

/** Human-readable documentation supplied by a scoring plugin for one rule. */
public record RuleDocumentation(String title, String markdown) {
    public RuleDocumentation {
        title = Objects.requireNonNull(title, "title must not be null");
        markdown = Objects.requireNonNull(markdown, "markdown must not be null");
    }
}
