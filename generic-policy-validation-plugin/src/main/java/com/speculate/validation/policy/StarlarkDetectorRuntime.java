package com.speculate.validation.policy;

import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Module;
import net.starlark.java.eval.Mutability;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkFloat;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkSemantics;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.syntax.FileOptions;
import net.starlark.java.syntax.ParserInput;

import com.google.common.collect.ImmutableMap;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs a detector written in Starlark. This is the default detector runtime;
 * {@link GroovyDetectorRuntime} is an opt-in fallback, disabled by default
 * until the detector sandbox lands because it is unsandboxed.
 *
 * <p>A detector source defines a top-level function {@code detect(api, rule)}
 * that returns a list of occurrence dicts ({@code pointer?}, {@code path?},
 * {@code message}). The {@code api} and {@code rule} values are deep-converted
 * to immutable Starlark structures, so a detector cannot mutate the host
 * model, perform I/O, use reflection, import anything, or recurse. The only
 * capabilities beyond pure list/dict/string work are the handful of builtins
 * in {@link StarlarkBuiltins}. Execution is bounded by a hard interpreter-step
 * cap.
 */
final class StarlarkDetectorRuntime {

    /** Deterministic upper bound on interpreter work per detector run. */
    private static final long MAX_EXECUTION_STEPS = 2_000_000L;

    /** Mirrors the Groovy runtime's cap on returned occurrences. */
    private static final int MAX_OCCURRENCES = 1_000;

    private final ImmutableMap<String, Object> predeclared;

    StarlarkDetectorRuntime() {
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
        Starlark.addMethods(builder, new StarlarkBuiltins());
        this.predeclared = builder.build();
    }

    /** Compiles the detector source, failing the bundle load on a syntax error. */
    void validate(Detector detector) {
        if (!"starlark".equals(detector.language())) {
            throw new BundleValidationException("Unsupported detector language '" + detector.language() + "'");
        }
        try (Mutability mu = Mutability.create("detector-validate")) {
            Module module = Module.withPredeclared(StarlarkSemantics.DEFAULT, predeclared);
            StarlarkThread thread = new StarlarkThread(mu, StarlarkSemantics.DEFAULT);
            thread.setMaxExecutionSteps(MAX_EXECUTION_STEPS);
            Starlark.execFile(ParserInput.fromString(detector.source(), "Detector.star"),
                    FileOptions.DEFAULT, module, thread);
            if (module.getGlobal("detect") == null) {
                throw new BundleValidationException("Detector '" + detector.id() + "' does not define detect(api, rule)");
            }
        } catch (BundleValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new BundleValidationException("Detector '" + detector.id() + "' does not compile: " + e.getMessage());
        }
    }

    List<Occurrence> execute(Detector detector, Map<String, Object> api, Rule rule) {
        if (!"starlark".equals(detector.language())) {
            throw new DetectorException("Unsupported detector language '" + detector.language() + "'");
        }
        try (Mutability mu = Mutability.create("detector-exec")) {
            Module module = Module.withPredeclared(StarlarkSemantics.DEFAULT, predeclared);
            StarlarkThread thread = new StarlarkThread(mu, StarlarkSemantics.DEFAULT);
            thread.setMaxExecutionSteps(MAX_EXECUTION_STEPS);
            thread.setPrintHandler((th, msg) -> { /* detectors do not print */ });

            Starlark.execFile(ParserInput.fromString(detector.source(), "Detector.star"),
                    FileOptions.DEFAULT, module, thread);
            Object detect = module.getGlobal("detect");
            if (detect == null) {
                throw new DetectorException("Detector '" + detector.id() + "' does not define detect(api, rule)");
            }

            Object result = Starlark.call(thread, detect,
                    List.of(toStarlark(api), toStarlark(rule.asMap())), Map.of());
            return toOccurrences(result);
        } catch (DetectorException e) {
            throw e;
        } catch (net.starlark.java.syntax.SyntaxError.Exception e) {
            throw new DetectorException("Detector does not compile: " + e.getMessage(), e);
        } catch (EvalException e) {
            throw new DetectorException("detect() failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DetectorException("detect() interrupted", e);
        } catch (RuntimeException e) {
            throw new DetectorException(e.toString(), e);
        }
    }

    // --- Java model -> immutable Starlark values ---------------------------

    private static Object toStarlark(Object value) {
        if (value == null) {
            return Starlark.NONE;
        }
        if (value instanceof String || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Integer i) {
            return StarlarkInt.of(i);
        }
        if (value instanceof Long l) {
            return StarlarkInt.of(l);
        }
        if (value instanceof java.math.BigInteger big) {
            return StarlarkInt.of(big);
        }
        if (value instanceof Number n) {
            // Enum values, numeric literals: keep them numeric so detectors
            // distinguish int/float exactly as the Groovy `instanceof` checks do.
            return StarlarkFloat.of(n.doubleValue());
        }
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> converted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                converted.put(String.valueOf(entry.getKey()), toStarlark(entry.getValue()));
            }
            return Dict.immutableCopyOf(converted);
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> converted = new ArrayList<>();
            for (Object element : iterable) {
                converted.add(toStarlark(element));
            }
            return StarlarkList.immutableCopyOf(converted);
        }
        return String.valueOf(value);
    }

    // --- Starlark result -> Occurrence list ------------------------------

    private static List<Occurrence> toOccurrences(Object result) {
        List<Occurrence> occurrences = new ArrayList<>();
        Iterable<?> rows;
        try {
            rows = Starlark.toIterable(result);
        } catch (EvalException e) {
            throw new DetectorException("detect() must return a list of occurrence dicts", e);
        }
        for (Object row : rows) {
            if (!(row instanceof Dict<?, ?> dict)) {
                throw new DetectorException("detect() must return a list of occurrence dicts");
            }
            Object message = dict.get("message");
            if (!(message instanceof String text) || text.isBlank()) {
                throw new DetectorException("Detector occurrence requires a non-blank message");
            }
            occurrences.add(new Occurrence(optionalString(dict.get("pointer")), optionalString(dict.get("path")), text));
            if (occurrences.size() > MAX_OCCURRENCES) {
                throw new DetectorException("Detector returned more than " + MAX_OCCURRENCES + " occurrences");
            }
        }
        return occurrences;
    }

    private static String optionalString(Object value) {
        if (value == null || value == Starlark.NONE) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw new DetectorException("Detector occurrence fields must be strings");
        }
        return text;
    }
}
