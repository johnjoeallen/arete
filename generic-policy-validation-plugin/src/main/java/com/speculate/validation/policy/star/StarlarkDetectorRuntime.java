package com.speculate.validation.policy.star;

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
 * POC (issue #125) — runs a detector written in Starlark instead of Groovy.
 *
 * <p>A detector source defines a top-level function {@code detect(api, rule)}
 * that returns a list of occurrence dicts ({@code pointer?}, {@code path},
 * {@code message}). The {@code api} and {@code rule} values are deep-converted
 * to immutable Starlark structures, so a detector physically cannot mutate the
 * host model, perform I/O, use reflection, import anything, or recurse. The
 * only capabilities beyond pure list/dict/string work are the handful of
 * builtins in {@link StarlarkBuiltins}.
 */
public final class StarlarkDetectorRuntime {

    /** Deterministic upper bound on interpreter work per detector run. */
    private static final long MAX_EXECUTION_STEPS = 2_000_000L;

    /** Mirrors the Groovy runtime's cap on returned occurrences. */
    private static final int MAX_OCCURRENCES = 1_000;

    private final ImmutableMap<String, Object> predeclared;

    public StarlarkDetectorRuntime() {
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
        Starlark.addMethods(builder, new StarlarkBuiltins());
        this.predeclared = builder.build();
    }

    /** Compiles the source (fails fast on a syntax error), same idea as GroovyDetectorRuntime.validate. */
    public void validate(String source) {
        try (Mutability mu = Mutability.create("detector-validate")) {
            Module module = Module.withPredeclared(StarlarkSemantics.DEFAULT, predeclared);
            StarlarkThread thread = new StarlarkThread(mu, StarlarkSemantics.DEFAULT);
            thread.setMaxExecutionSteps(MAX_EXECUTION_STEPS);
            Starlark.execFile(ParserInput.fromString(source, "Detector.star"), FileOptions.DEFAULT, module, thread);
            if (module.getGlobal("detect") == null) {
                throw new DetectorScriptException("Detector.star does not define detect(api, rule)");
            }
        } catch (DetectorScriptException e) {
            throw e;
        } catch (Exception e) {
            throw new DetectorScriptException("Detector.star does not compile: " + e.getMessage(), e);
        }
    }

    /** Runs {@code detect(api, rule)} and normalises the result to plain maps. */
    public List<Map<String, String>> execute(String source, Map<String, Object> api, Map<String, Object> rule) {
        try (Mutability mu = Mutability.create("detector-exec")) {
            Module module = Module.withPredeclared(StarlarkSemantics.DEFAULT, predeclared);
            StarlarkThread thread = new StarlarkThread(mu, StarlarkSemantics.DEFAULT);
            thread.setMaxExecutionSteps(MAX_EXECUTION_STEPS);
            thread.setPrintHandler((th, msg) -> { /* detectors do not print */ });

            Starlark.execFile(ParserInput.fromString(source, "Detector.star"), FileOptions.DEFAULT, module, thread);
            Object detect = module.getGlobal("detect");
            if (detect == null) {
                throw new DetectorScriptException("Detector.star does not define detect(api, rule)");
            }

            Object apiValue = toStarlark(api);
            Object ruleValue = toStarlark(rule);
            Object result = Starlark.call(thread, detect, List.of(apiValue, ruleValue), Map.of());
            return toOccurrences(result);
        } catch (DetectorScriptException e) {
            throw e;
        } catch (EvalException e) {
            throw new DetectorScriptException("detect() failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DetectorScriptException("detect() interrupted", e);
        } catch (Exception e) {
            throw new DetectorScriptException("detect() error: " + e, e);
        }
    }

    // --- Java model -> immutable Starlark values -----------------------------

    @SuppressWarnings("unchecked")
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
            // can distinguish int/float exactly as the Groovy `instanceof` checks do.
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

    // --- Starlark result -> plain occurrence maps ---------------------------

    private static List<Map<String, String>> toOccurrences(Object result) throws EvalException {
        List<Map<String, String>> occurrences = new ArrayList<>();
        Iterable<?> rows = Starlark.toIterable(result);
        for (Object row : rows) {
            if (!(row instanceof Dict<?, ?> dict)) {
                throw new DetectorScriptException("detect() must return a list of dicts");
            }
            Object message = dict.get("message");
            if (!(message instanceof String text) || text.isBlank()) {
                throw new DetectorScriptException("occurrence requires a non-blank message");
            }
            Map<String, String> occurrence = new LinkedHashMap<>();
            Object pointer = dict.get("pointer");
            if (pointer instanceof String pointerText) {
                occurrence.put("pointer", pointerText);
            }
            Object path = dict.get("path");
            if (path instanceof String pathText) {
                occurrence.put("path", pathText);
            }
            occurrence.put("message", text);
            occurrences.add(occurrence);
            if (occurrences.size() > MAX_OCCURRENCES) {
                throw new DetectorScriptException("detect() returned more than " + MAX_OCCURRENCES + " occurrences");
            }
        }
        return occurrences;
    }

    /** POC failure signal; the real runtime would map this to ValidationResult.pluginError. */
    public static final class DetectorScriptException extends RuntimeException {
        DetectorScriptException(String message) {
            super(message);
        }

        DetectorScriptException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
