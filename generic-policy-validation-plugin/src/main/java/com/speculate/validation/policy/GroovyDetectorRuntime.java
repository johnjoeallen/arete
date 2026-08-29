package com.speculate.validation.policy;

import groovy.lang.Closure;
import groovy.lang.GroovyShell;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Runs bundle Groovy directly in the plugin JVM via a bare {@code GroovyShell}
 * — no sandbox. It is an opt-in fallback ({@code detector-language=groovy}),
 * disabled by default until the detector sandbox is available. Not deprecated:
 * kept parity-equivalent to the Starlark runtime and re-enabled once it can be
 * run safely. See {@code docs/policy-engine-sandbox-plan.md}.
 */
final class GroovyDetectorRuntime {
    void validate(Detector detector) {
        if (!"groovy".equals(detector.language())) throw new BundleValidationException("Unsupported detector language '" + detector.language() + "'");
        try {
            new GroovyShell().parse(detector.source());
        } catch (Exception e) {
            throw new BundleValidationException("Detector '" + detector.id() + "' does not compile: " + e.getMessage());
        }
    }

    List<Occurrence> execute(Detector detector, Map<String, Object> api, Rule rule) {
        if (!"groovy".equals(detector.language())) throw new DetectorException("Unsupported detector language '" + detector.language() + "'");
        try {
            Object value = new GroovyShell().evaluate(detector.source());
            if (!(value instanceof Closure<?> closure)) throw new DetectorException("Detector source must evaluate to a closure accepting api and rule maps");
            Object rawOccurrences = closure.call(api, rule.asMap());
            if (!(rawOccurrences instanceof Collection<?> collection)) throw new DetectorException("Detector closure must return a collection of occurrence maps");
            if (collection.size() > 1_000) throw new DetectorException("Detector returned more than 1000 occurrences");
            List<Occurrence> occurrences = new ArrayList<>(collection.size());
            for (Object raw : collection) {
                if (!(raw instanceof Map<?, ?> map)) throw new DetectorException("Detector returned a non-map occurrence");
                Object message = map.get("message");
                if (!(message instanceof String text) || text.isBlank()) throw new DetectorException("Detector occurrence requires a non-blank message");
                occurrences.add(new Occurrence(optionalString(map.get("pointer")), optionalString(map.get("path")), text));
            }
            return occurrences;
        } catch (DetectorException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DetectorException(e.toString(), e);
        }
    }

    private static String optionalString(Object value) {
        if (value == null) return null;
        if (!(value instanceof String text)) throw new DetectorException("Detector occurrence fields must be strings");
        return text;
    }
}
