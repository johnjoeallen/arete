package net.dublinux.arete.validation.policy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Runs one rule invocation in a disposable JVM. */
final class ForkedMatcherEvaluator {
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private final ObjectMapper mapper = new ObjectMapper();
    private final long timeoutMillis;

    ForkedMatcherEvaluator(long timeoutMillis) {
        if (timeoutMillis < 1) throw new IllegalArgumentException("forked rule timeout must be positive");
        this.timeoutMillis = timeoutMillis;
    }

    List<Diagnostic> execute(Matcher matcher, Map<String, Object> api, PolicyRule policyRule) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("matcher", Map.of("id", matcher.id(), "language", matcher.language(), "source", matcher.source(),
                "scopes", matcher.scopes(), "parameters", matcher.parameters()));
        request.put("api", api);
        request.put("policyRule", Map.of("id", policyRule.id(), "title", policyRule.title(), "category", policyRule.category(),
                "matcherId", policyRule.matcherId(), "scope", policyRule.scope(), "parameters", policyRule.parameters()));
        Process process = null;
        try {
            process = new ProcessBuilder(javaExecutable(), "-cp", System.getProperty("java.class.path"),
                    ForkedRuleWorker.class.getName()).redirectErrorStream(false).start();
            try (OutputStream input = process.getOutputStream()) {
                input.write((mapper.writeValueAsString(request) + "\n").getBytes(StandardCharsets.UTF_8));
            }
            if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new MatcherEvaluationException("forked rule timed out after " + timeoutMillis + "ms");
            }
            String response = readBounded(process.getInputStream());
            String error = readBounded(process.getErrorStream());
            if (process.exitValue() != 0) {
                throw new MatcherEvaluationException("forked rule exited with " + process.exitValue()
                        + (error.isBlank() ? "" : ": " + error.trim()));
            }
            Map<String, Object> envelope = mapper.readValue(response, MAP_TYPE);
            if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                throw new MatcherEvaluationException("forked rule failed: " + envelope.get("error"));
            }
            return mapper.convertValue(envelope.get("diagnostics"), new TypeReference<List<Diagnostic>>() { });
        } catch (MatcherEvaluationException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) process.destroyForcibly();
            throw new MatcherEvaluationException("forked rule interrupted", e);
        } catch (Exception e) {
            if (process != null) process.destroyForcibly();
            throw new MatcherEvaluationException("forked rule protocol failed: " + e.getMessage(), e);
        }
    }

    private static String javaExecutable() {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        return Files.isExecutable(java) ? java.toString() : "java";
    }

    private static String readBounded(InputStream stream) throws IOException {
        byte[] bytes = stream.readNBytes(MAX_RESPONSE_BYTES + 1);
        if (bytes.length > MAX_RESPONSE_BYTES) throw new IOException("forked rule response exceeded limit");
        return new String(bytes, StandardCharsets.UTF_8).trim();
    }
}

/** Child-process entry point for {@link ForkedMatcherEvaluator}. */
final class ForkedRuleWorker {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private ForkedRuleWorker() { }

    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try {
            Map<String, Object> request = mapper.readValue(System.in, MAP_TYPE);
            Map<String, Object> definitionData = object(request, "matcher");
            Matcher matcher = new Matcher(string(definitionData, "id"), string(definitionData, "language"),
                    string(definitionData, "source"), mapper.convertValue(definitionData.get("scopes"), new TypeReference<List<String>>() { }),
                    mapper.convertValue(definitionData.get("parameters"), new TypeReference<Map<String, ParameterDefinition>>() { }));
            Map<String, Object> policyData = object(request, "policyRule");
            PolicyRule policyRule = new PolicyRule(string(policyData, "id"), string(policyData, "title"), string(policyData, "category"),
                    string(policyData, "matcherId"), string(policyData, "scope"),
                    mapper.convertValue(policyData.get("parameters"), new TypeReference<Map<String, Object>>() { }), "");
            List<Diagnostic> diagnostics = switch (matcher.language()) {
                case "groovy" -> new GroovyMatcherEvaluator().execute(matcher, object(request, "api"), policyRule);
                case "distill" -> new DistillMatcherEvaluator().execute(matcher, object(request, "api"), policyRule);
                default -> throw new MatcherEvaluationException("Unsupported matcher language: " + matcher.language());
            };
            System.out.println(mapper.writeValueAsString(Map.of("ok", true, "diagnostics", diagnostics)));
        } catch (Exception e) {
            System.out.println(mapper.writeValueAsString(Map.of("ok", false, "error", e.toString())));
            System.exit(1);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (!(value instanceof Map<?, ?>)) throw new IllegalArgumentException("request field '" + key + "' must be an object");
        return (Map<String, Object>) value;
    }

    private static String string(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (!(value instanceof String text)) throw new IllegalArgumentException("request field '" + key + "' must be a string");
        return text;
    }
}
