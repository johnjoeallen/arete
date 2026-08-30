package net.dublinux.arete.validation.policy;

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
 * Runs the policy engine's Starlark rules.
 *
 * <p>A rule source defines a top-level function {@code detect(api, rule)}
 * that returns a list of diagnostic dicts ({@code pointer?}, {@code path?},
 * {@code message}). The {@code api} and {@code rule} values are deep-converted
 * to immutable Starlark structures, so a rule cannot mutate the host
 * model, perform I/O, use reflection, import anything, or recurse. The only
 * capabilities beyond pure list/dict/string work are the handful of builtins
 * in {@link StarlarkBuiltins}. Execution is bounded by a hard interpreter-step
 * cap.
 */
final class StarlarkMatcherEvaluator {

    /** Deterministic upper bound on interpreter work per rule run. */
    private static final long MAX_EXECUTION_STEPS = 2_000_000L;

    /** Caps returned diagnostics to protect the host from runaway rules. */
    private static final int MAX_DIAGNOSTICS = 1_000;

    private final ImmutableMap<String, Object> predeclared;

    StarlarkMatcherEvaluator() {
        ImmutableMap.Builder<String, Object> builder = ImmutableMap.builder();
        Starlark.addMethods(builder, new StarlarkBuiltins());
        this.predeclared = builder.build();
    }

    /** Compiles the rule source, failing the bundle load on a syntax error. */
    void validate(Matcher rule) {
        if (!"starlark".equals(rule.language())) {
            throw new BundleValidationException("Unsupported rule language '" + rule.language() + "'");
        }
        try (Mutability mu = Mutability.create("rule-validate")) {
            Module module = Module.withPredeclared(StarlarkSemantics.DEFAULT, predeclared);
            StarlarkThread thread = new StarlarkThread(mu, StarlarkSemantics.DEFAULT);
            thread.setMaxExecutionSteps(MAX_EXECUTION_STEPS);
            Starlark.execFile(ParserInput.fromString(rule.source(), "Matcher.star"),
                    FileOptions.DEFAULT, module, thread);
            if (module.getGlobal("detect") == null) {
                throw new BundleValidationException("Matcher '" + rule.id() + "' does not define detect(api, rule)");
            }
        } catch (BundleValidationException e) {
            throw e;
        } catch (Exception e) {
            throw new BundleValidationException("Matcher '" + rule.id() + "' does not compile: " + e.getMessage());
        }
    }

    List<Diagnostic> execute(Matcher matcher, Map<String, Object> api, PolicyRule rule) {
        if (!"starlark".equals(matcher.language())) {
            throw new MatcherEvaluationException("Unsupported rule language '" + matcher.language() + "'");
        }
        try (Mutability mu = Mutability.create("rule-exec")) {
            Module module = Module.withPredeclared(StarlarkSemantics.DEFAULT, predeclared);
            StarlarkThread thread = new StarlarkThread(mu, StarlarkSemantics.DEFAULT);
            thread.setMaxExecutionSteps(MAX_EXECUTION_STEPS);
            thread.setPrintHandler((th, msg) -> { /* rules do not print */ });

            Starlark.execFile(ParserInput.fromString(matcher.source(), "Matcher.star"),
                    FileOptions.DEFAULT, module, thread);
            Object detect = module.getGlobal("detect");
            if (detect == null) {
                throw new MatcherEvaluationException("Matcher '" + rule.id() + "' does not define detect(api, rule)");
            }

            Object result = Starlark.call(thread, detect,
                    List.of(toStarlark(api), toStarlark(rule.asMap())), Map.of());
            return toDiagnostics(result);
        } catch (MatcherEvaluationException e) {
            throw e;
        } catch (net.starlark.java.syntax.SyntaxError.Exception e) {
            throw new MatcherEvaluationException("Matcher does not compile: " + e.getMessage(), e);
        } catch (EvalException e) {
            throw new MatcherEvaluationException("detect() failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MatcherEvaluationException("detect() interrupted", e);
        } catch (RuntimeException e) {
            throw new MatcherEvaluationException(e.toString(), e);
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
            // Enum values, numeric literals: keep them numeric so rules
            // Preserve the distinction between integer and floating-point values.
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

    // --- Starlark result -> Diagnostic list ------------------------------

    private static List<Diagnostic> toDiagnostics(Object result) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        Iterable<?> rows;
        try {
            rows = Starlark.toIterable(result);
        } catch (EvalException e) {
            throw new MatcherEvaluationException("detect() must return a list of diagnostic dicts", e);
        }
        for (Object row : rows) {
            if (!(row instanceof Dict<?, ?> dict)) {
                throw new MatcherEvaluationException("detect() must return a list of diagnostic dicts");
            }
            Object message = dict.get("message");
            if (!(message instanceof String text) || text.isBlank()) {
                throw new MatcherEvaluationException("Matcher diagnostic requires a non-blank message");
            }
            diagnostics.add(new Diagnostic(optionalString(dict.get("pointer")), optionalString(dict.get("path")), text));
            if (diagnostics.size() > MAX_DIAGNOSTICS) {
                throw new MatcherEvaluationException("Matcher returned more than " + MAX_DIAGNOSTICS + " diagnostics");
            }
        }
        return diagnostics;
    }

    private static String optionalString(Object value) {
        if (value == null || value == Starlark.NONE) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw new MatcherEvaluationException("Matcher diagnostic fields must be strings");
        }
        return text;
    }
}
