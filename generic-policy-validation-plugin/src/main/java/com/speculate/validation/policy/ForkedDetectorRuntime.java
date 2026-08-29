package com.speculate.validation.policy;

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

/** Runs one detector invocation in a disposable JVM. */
final class ForkedDetectorRuntime {
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private final ObjectMapper mapper = new ObjectMapper();
    private final long timeoutMillis;

    ForkedDetectorRuntime(long timeoutMillis) {
        if (timeoutMillis < 1) throw new IllegalArgumentException("forked detector timeout must be positive");
        this.timeoutMillis = timeoutMillis;
    }

    List<Occurrence> execute(Detector detector, Map<String, Object> api, Rule rule) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("detector", Map.of("id", detector.id(), "language", detector.language(), "source", detector.source(),
                "scopes", detector.scopes(), "parameters", detector.parameters()));
        request.put("api", api);
        request.put("rule", Map.of("id", rule.id(), "title", rule.title(), "category", rule.category(),
                "detector", rule.detector(), "scope", rule.scope(), "parameters", rule.parameters()));
        Process process = null;
        try {
            process = new ProcessBuilder(javaExecutable(), "-cp", System.getProperty("java.class.path"),
                    ForkedDetectorWorker.class.getName()).redirectErrorStream(false).start();
            try (OutputStream input = process.getOutputStream()) {
                input.write((mapper.writeValueAsString(request) + "\n").getBytes(StandardCharsets.UTF_8));
            }
            if (!process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)) {
                process.destroyForcibly();
                throw new DetectorException("forked detector timed out after " + timeoutMillis + "ms");
            }
            String response = readBounded(process.getInputStream());
            String error = readBounded(process.getErrorStream());
            if (process.exitValue() != 0) {
                throw new DetectorException("forked detector exited with " + process.exitValue()
                        + (error.isBlank() ? "" : ": " + error.trim()));
            }
            Map<String, Object> envelope = mapper.readValue(response, MAP_TYPE);
            if (!Boolean.TRUE.equals(envelope.get("ok"))) {
                throw new DetectorException("forked detector failed: " + envelope.get("error"));
            }
            return mapper.convertValue(envelope.get("occurrences"), new TypeReference<List<Occurrence>>() { });
        } catch (DetectorException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) process.destroyForcibly();
            throw new DetectorException("forked detector interrupted", e);
        } catch (Exception e) {
            if (process != null) process.destroyForcibly();
            throw new DetectorException("forked detector protocol failed: " + e.getMessage(), e);
        }
    }

    private static String javaExecutable() {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        return Files.isExecutable(java) ? java.toString() : "java";
    }

    private static String readBounded(InputStream stream) throws IOException {
        byte[] bytes = stream.readNBytes(MAX_RESPONSE_BYTES + 1);
        if (bytes.length > MAX_RESPONSE_BYTES) throw new IOException("forked detector response exceeded limit");
        return new String(bytes, StandardCharsets.UTF_8).trim();
    }
}

/** Child-process entry point for {@link ForkedDetectorRuntime}. */
final class ForkedDetectorWorker {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private ForkedDetectorWorker() { }

    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        try {
            Map<String, Object> request = mapper.readValue(System.in, MAP_TYPE);
            Map<String, Object> detectorData = object(request, "detector");
            Detector detector = new Detector(string(detectorData, "id"), string(detectorData, "language"),
                    string(detectorData, "source"), mapper.convertValue(detectorData.get("scopes"), new TypeReference<List<String>>() { }),
                    mapper.convertValue(detectorData.get("parameters"), new TypeReference<Map<String, ParameterDefinition>>() { }));
            Map<String, Object> ruleData = object(request, "rule");
            Rule rule = new Rule(string(ruleData, "id"), string(ruleData, "title"), string(ruleData, "category"),
                    string(ruleData, "detector"), string(ruleData, "scope"),
                    mapper.convertValue(ruleData.get("parameters"), new TypeReference<Map<String, Object>>() { }), "");
            List<Occurrence> occurrences = switch (detector.language()) {
                case "groovy" -> new GroovyDetectorRuntime().execute(detector, object(request, "api"), rule);
                case "sift" -> new SiftRuntime().execute(detector, object(request, "api"), rule);
                default -> new StarlarkDetectorRuntime().execute(detector, object(request, "api"), rule);
            };
            System.out.println(mapper.writeValueAsString(Map.of("ok", true, "occurrences", occurrences)));
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
