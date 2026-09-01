package net.dublinux.arete.validation.policy;

import groovy.lang.Closure;
import groovy.lang.GroovyShell;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Runs bundle Groovy directly in the plugin JVM via a bare {@code GroovyShell}
 * — no sandbox. It is available as a fallback because it executes
 * bundle-supplied code.
 */
final class GroovyMatcherEvaluator {
    void validate(Matcher rule) {
        if (!"groovy".equals(rule.language())) throw new BundleValidationException("Unsupported rule language '" + rule.language() + "'");
        try {
            new GroovyShell().parse(rule.source());
        } catch (Exception e) {
            throw new BundleValidationException("Matcher '" + rule.id() + "' does not compile: " + e.getMessage());
        }
    }

    List<Diagnostic> execute(Matcher matcher, Map<String, Object> api, PolicyRule rule) {
        if (!"groovy".equals(matcher.language())) throw new MatcherEvaluationException("Unsupported rule language '" + matcher.language() + "'");
        try {
            Object value = new GroovyShell().evaluate(matcher.source());
            if (!(value instanceof Closure<?> closure)) throw new MatcherEvaluationException("Matcher source must evaluate to a closure accepting api and rule maps");
            Object rawDiagnostics = closure.call(api, rule.asMap());
            if (!(rawDiagnostics instanceof Collection<?> collection)) throw new MatcherEvaluationException("Matcher closure must return a collection of diagnostic maps");
            if (collection.size() > 1_000) throw new MatcherEvaluationException("Matcher returned more than 1000 diagnostics");
            List<Diagnostic> diagnostics = new ArrayList<>(collection.size());
            for (Object raw : collection) {
                if (!(raw instanceof Map<?, ?> map)) throw new MatcherEvaluationException("Matcher returned a non-map diagnostic");
                String text = optionalString(map.get("message"));
                if (text == null || text.isBlank()) throw new MatcherEvaluationException("Matcher diagnostic requires a non-blank message");
                diagnostics.add(new Diagnostic(optionalString(map.get("pointer")), optionalString(map.get("path")), text));
            }
            return diagnostics;
        } catch (MatcherEvaluationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new MatcherEvaluationException(e.toString(), e);
        }
    }

    private static String optionalString(Object value) {
        if (value == null) return null;
        // Groovy string interpolation yields GString (a CharSequence, not String).
        if (value instanceof CharSequence text) return text.toString();
        throw new MatcherEvaluationException("Matcher diagnostic fields must be strings");
    }
}
