package com.speculate.validation.policy;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loads and validates every declarative resource and detector reference. */
final class PolicyBundleLoader {
    private final Yaml yaml;
    private final GroovyDetectorRuntime groovyRuntime = new GroovyDetectorRuntime();
    private final StarlarkDetectorRuntime starlarkRuntime = new StarlarkDetectorRuntime();
    private final SiftRuntime siftRuntime = new SiftRuntime();

    /** The detector source file each supported language is loaded from. */
    private static final Map<String, String> SOURCE_FILE = Map.of(
            "sift", "Detector.sift",
            "starlark", "Detector.star",
            "groovy", "Detector.groovy");

    /**
     * Ordered list of detector languages to try for each detector. The first
     * language in the list that has a source file present wins, so a detector
     * shipping only {@code Detector.groovy} runs on Groovy even while a
     * detector that also ships {@code Detector.star} stays on Starlark.
     *
     * <p>The default is {@code ["sift", "starlark"]}: Sift is the primary
     * language, with Starlark as the fallback for any detector that ships only
     * a {@code Detector.star}. Groovy is always opt-in. Callers override this
     * (e.g. {@code ["starlark"]} to pin Starlark, or {@code ["groovy",
     * "starlark"]} to prefer Groovy) via configuration.
     */
    record LoadOptions(List<String> languagePrecedence) {
        LoadOptions {
            if (languagePrecedence == null || languagePrecedence.isEmpty()) {
                throw new BundleValidationException("detector language precedence must not be empty");
            }
            for (String language : languagePrecedence) {
                if (!SOURCE_FILE.containsKey(language)) {
                    throw new BundleValidationException("unknown detector language '" + language
                            + "'; supported: " + SOURCE_FILE.keySet());
                }
            }
            languagePrecedence = List.copyOf(languagePrecedence);
        }
        static LoadOptions defaults() { return new LoadOptions(List.of("sift", "starlark")); }
    }

    PolicyBundleLoader() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(20);
        yaml = new Yaml(new SafeConstructor(options));
    }

    PolicyBundle load(BundleResources resources) {
        return load(resources, LoadOptions.defaults());
    }

    PolicyBundle load(BundleResources resources, LoadOptions loadOptions) {
        Map<String, Object> manifest = yamlMap("PolicyBundle.yaml", resources.read("PolicyBundle.yaml"));
        rejectUnknown("PolicyBundle.yaml", manifest, Set.of("formatVersion", "bundleId", "bundleVersion", "rules", "policies", "detectors"));
        if (!Integer.valueOf(1).equals(manifest.get("formatVersion"))) throw new BundleValidationException("PolicyBundle.yaml: formatVersion must be 1");
        Map<String, String> rulePaths = stringMap("PolicyBundle.yaml", "rules", manifest.get("rules"));
        Map<String, String> policyPaths = stringMap("PolicyBundle.yaml", "policies", manifest.get("policies"));
        Map<String, String> detectorPaths = stringMap("PolicyBundle.yaml", "detectors", manifest.get("detectors"));
        if (rulePaths.isEmpty() || policyPaths.isEmpty() || detectorPaths.isEmpty()) throw new BundleValidationException("PolicyBundle.yaml: rules, policies, and detectors must not be empty");

        Map<String, Detector> detectors = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : detectorPaths.entrySet()) {
            String descriptorPath = safePath("PolicyBundle.yaml", entry.getValue());
            Detector descriptor = parseDetector(descriptorPath, resources.read(descriptorPath));
            if (!entry.getKey().equals(descriptor.id())) throw new BundleValidationException(descriptorPath + ": manifest detector id does not match descriptor id");

            Detector detector = null;
            for (String language : loadOptions.languagePrecedence()) {
                String source = optionalRead(resources, siblingPath(descriptorPath, SOURCE_FILE.get(language)));
                if (source == null) continue;
                detector = new Detector(descriptor.id(), language, source, descriptor.scopes(), descriptor.parameters());
                switch (language) {
                    case "groovy" -> groovyRuntime.validate(detector);
                    case "sift" -> siftRuntime.validate(detector);
                    default -> starlarkRuntime.validate(detector);
                }
                break;
            }
            if (detector == null) {
                throw new BundleValidationException(descriptorPath + ": no detector source for languages "
                        + loadOptions.languagePrecedence());
            }
            detectors.put(detector.id(), detector);
        }

        Map<String, Rule> rules = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : rulePaths.entrySet()) {
            String path = safePath("PolicyBundle.yaml", entry.getValue());
            Rule rule = parseRule(path, resources.read(path));
            if (!entry.getKey().equals(rule.id())) throw new BundleValidationException(path + ": manifest rule id does not match rule id");
            Detector detector = detectors.get(rule.detector());
            // A catalogue can document rules before their reusable detector
            // ships. Keep those rules loadable, but only validate parameters
            // for detector capabilities that are currently available.
            if (detector != null) validateRule(path, rule, detector);
            rules.put(rule.id(), rule);
        }

        Map<String, Policy> policies = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : policyPaths.entrySet()) {
            String path = safePath("PolicyBundle.yaml", entry.getValue());
            Policy policy = parsePolicy(path, resources.read(path), rules, detectors);
            if (!entry.getKey().equals(policy.id())) throw new BundleValidationException(path + ": manifest policy id does not match policy id");
            for (String ruleId : policy.dispositions().keySet()) if (!rules.containsKey(ruleId)) throw new BundleValidationException(path + ": unknown policy rule '" + ruleId + "'");
            policies.put(policy.id(), policy);
        }
        return new PolicyBundle(rules, policies, detectors);
    }

    private Detector parseDetector(String path, String content) {
        Map<String, Object> data = frontMatter(path, content);
        rejectUnknown(path, data, Set.of("id", "language", "source", "scopes", "parameters"));
        Map<String, ParameterDefinition> parameters = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map(path, "parameters", data.get("parameters")).entrySet()) {
            Map<String, Object> definition = map(path, "parameters." + entry.getKey(), entry.getValue());
            rejectUnknown(path, definition, Set.of("type", "required", "values"));
            String type = requiredString(path, "parameters." + entry.getKey() + ".type", definition.get("type"));
            if (!(definition.get("required") instanceof Boolean required)) throw new BundleValidationException(path + ": parameters." + entry.getKey() + ".required must be boolean");
            List<String> values = definition.containsKey("values")
                    ? stringList(path, "parameters." + entry.getKey() + ".values", definition.get("values"))
                    : List.of();
            if ("enum".equals(type) && values.isEmpty()) {
                throw new BundleValidationException(path + ": enum parameter '" + entry.getKey() + "' requires non-empty values");
            }
            if (("string".equals(type) || "integer".equals(type) || "boolean".equals(type)) && !values.isEmpty()) {
                throw new BundleValidationException(path + ": " + type + " parameter '" + entry.getKey() + "' must not declare values");
            }
            if (!Set.of("enum", "string", "integer", "boolean").contains(type)) {
                throw new BundleValidationException(path + ": unsupported parameter type '" + type + "'");
            }
            parameters.put(entry.getKey(), new ParameterDefinition(type, required, values));
        }
        return new Detector(requiredString(path, "id", data.get("id")), requiredString(path, "language", data.get("language")), requiredString(path, "source", data.get("source")), stringList(path, "scopes", data.get("scopes")), Map.copyOf(parameters));
    }

    private Rule parseRule(String path, String content) {
        Map<String, Object> data = frontMatter(path, content);
        rejectUnknown(path, data, Set.of("id", "category", "detector", "scope", "parameters"));
        Object rawParameters = data.get("parameters");
        Map<String, Object> parameters = rawParameters == null ? Map.of() : map(path, "parameters", rawParameters);
        return new Rule(requiredString(path, "id", data.get("id")), title(path, content), requiredString(path, "category", data.get("category")), requiredString(path, "detector", data.get("detector")), requiredString(path, "scope", data.get("scope")), Map.copyOf(parameters), markdownBody(path, content));
    }

    private Policy parsePolicy(String path, String content, Map<String, Rule> rules, Map<String, Detector> detectors) {
        Map<String, Object> data = frontMatter(path, content);
        rejectUnknown(path, data, Set.of("id", "rules"));
        Map<String, PolicyDisposition> dispositions = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map(path, "rules", data.get("rules")).entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Number number && validPoints(number)) {
                dispositions.put(entry.getKey(), new Deduction(number.doubleValue()));
            } else if ("PROHIBITED".equals(value)) {
                dispositions.put(entry.getKey(), new Prohibited());
            } else if (value instanceof Map<?, ?> raw) {
                Map<String, Object> declaration = map(path, "rules." + entry.getKey(), raw);
                rejectUnknown(path, declaration, Set.of("points", "parameters"));
                Object points = declaration.get("points");
                Map<String, Object> overrides = declaration.containsKey("parameters")
                        ? map(path, "rules." + entry.getKey() + ".parameters", declaration.get("parameters"))
                        : Map.of();
                Rule rule = rules.get(entry.getKey());
                Detector detector = rule == null ? null : detectors.get(rule.detector());
                if (detector != null) validateParameterOverrides(path, entry.getKey(), overrides, detector);
                if ("PROHIBITED".equals(points)) dispositions.put(entry.getKey(), new Prohibited(overrides));
                else if (points instanceof Number number && validPoints(number)) dispositions.put(entry.getKey(), new Deduction(number.doubleValue(), overrides));
                else throw new BundleValidationException(path + ": " + entry.getKey() + ".points must be a number from 0 to 100 or PROHIBITED");
            } else throw new BundleValidationException(path + ": " + entry.getKey() + " must be a number, PROHIBITED, or a declaration with points and parameters");
        }
        return new Policy(requiredString(path, "id", data.get("id")), dispositions);
    }

    private static boolean validPoints(Number number) {
        return Double.isFinite(number.doubleValue()) && number.doubleValue() >= 0 && number.doubleValue() <= 100;
    }

    private static void validateParameterOverrides(String path, String ruleId, Map<String, Object> overrides, Detector detector) {
        for (Map.Entry<String, Object> parameter : overrides.entrySet()) {
            ParameterDefinition definition = detector.parameters().get(parameter.getKey());
            if (definition == null) throw new BundleValidationException(path + ": " + ruleId + " overrides unknown parameter '" + parameter.getKey() + "'");
            if (!validParameterValue(definition, parameter.getValue())) {
                throw new BundleValidationException(path + ": " + ruleId + " has invalid " + definition.type() + " override for parameter '" + parameter.getKey() + "'");
            }
        }
    }

    private static void validateRule(String path, Rule rule, Detector detector) {
        if (!detector.scopes().contains(rule.scope())) throw new BundleValidationException(path + ": scope '" + rule.scope() + "' is not supported by detector '" + detector.id() + "'");
        for (Map.Entry<String, Object> parameter : rule.parameters().entrySet()) {
            ParameterDefinition definition = detector.parameters().get(parameter.getKey());
            if (definition == null) throw new BundleValidationException(path + ": unknown parameter '" + parameter.getKey() + "' for detector '" + detector.id() + "'");
            if (!validParameterValue(definition, parameter.getValue())) {
                throw new BundleValidationException(path + ": invalid " + definition.type() + " value for parameter '" + parameter.getKey() + "'");
            }
        }
        for (Map.Entry<String, ParameterDefinition> parameter : detector.parameters().entrySet()) {
            if (parameter.getValue().required() && !rule.parameters().containsKey(parameter.getKey())) {
                throw new BundleValidationException(path + ": missing required parameter '" + parameter.getKey() + "' for detector '" + detector.id() + "'");
            }
        }
    }

    /** Validates values before script execution so scripts can rely on their descriptor contract. */
    private static boolean validParameterValue(ParameterDefinition definition, Object value) {
        return switch (definition.type()) {
            case "enum" -> value instanceof String text && definition.values().contains(text);
            case "string" -> value instanceof String text && !text.isBlank();
            case "boolean" -> value instanceof Boolean;
            case "integer" -> value instanceof Number number && number.doubleValue() == Math.rint(number.doubleValue());
            default -> false; // parseDetector rejects unknown types; retain defensive behaviour here.
        };
    }

    private Map<String, Object> frontMatter(String path, String content) {
        String[] lines = content.replace("\r\n", "\n").split("\n", -1);
        if (lines.length < 3 || !"---".equals(lines[0])) throw new BundleValidationException(path + ": expected YAML front matter starting with ---");
        int end = -1;
        for (int index = 1; index < lines.length; index++) if ("---".equals(lines[index])) { end = index; break; }
        if (end < 0) throw new BundleValidationException(path + ": unterminated YAML front matter");
        return yamlMap(path, String.join("\n", Arrays.copyOfRange(lines, 1, end)));
    }

    private static String markdownBody(String path, String content) {
        String[] lines = content.replace("\r\n", "\n").split("\n", -1);
        if (lines.length < 3 || !"---".equals(lines[0])) throw new BundleValidationException(path + ": expected YAML front matter starting with ---");
        for (int index = 1; index < lines.length; index++) {
            if ("---".equals(lines[index])) return String.join("\n", Arrays.copyOfRange(lines, index + 1, lines.length)).trim();
        }
        throw new BundleValidationException(path + ": unterminated YAML front matter");
    }

    private Map<String, Object> yamlMap(String path, String document) {
        try { return map(path, "document", yaml.load(document)); }
        catch (RuntimeException e) { throw new BundleValidationException(path + ": invalid YAML: " + e.getMessage()); }
    }

    private static String title(String path, String content) {
        for (String line : content.replace("\r\n", "\n").split("\n")) if (line.startsWith("# ")) return line.substring(2).trim();
        throw new BundleValidationException(path + ": expected a level-one Markdown heading");
    }

    private static Map<String, Object> map(String path, String field, Object value) {
        if (!(value instanceof Map<?, ?> raw)) throw new BundleValidationException(path + ": " + field + " must be a mapping");
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key)) throw new BundleValidationException(path + ": " + field + " keys must be strings");
            result.put(key, entry.getValue());
        }
        return result;
    }

    private static Map<String, String> stringMap(String path, String field, Object value) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map(path, field, value).entrySet()) result.put(entry.getKey(), requiredString(path, field + "." + entry.getKey(), entry.getValue()));
        return result;
    }

    private static List<String> stringList(String path, String field, Object value) {
        if (!(value instanceof List<?> list)) throw new BundleValidationException(path + ": " + field + " must be a list");
        List<String> result = new ArrayList<>();
        for (Object item : list) result.add(requiredString(path, field, item));
        return List.copyOf(result);
    }

    private static String requiredString(String path, String field, Object value) {
        if (!(value instanceof String text) || text.isBlank()) throw new BundleValidationException(path + ": " + field + " must be a non-blank string");
        return text;
    }

    private static void rejectUnknown(String path, Map<String, Object> values, Set<String> allowed) {
        for (String field : values.keySet()) if (!allowed.contains(field)) throw new BundleValidationException(path + ": unknown field '" + field + "'");
    }

    private static String safePath(String referringPath, String path) {
        if (path.startsWith("/") || path.contains("\\") || path.contains("..") || path.isBlank()) throw new BundleValidationException(referringPath + ": unsafe resource path '" + path + "'");
        return path;
    }

    private static String siblingPath(String descriptorPath, String source) {
        int slash = descriptorPath.lastIndexOf('/');
        return safePath(descriptorPath, descriptorPath.substring(0, slash + 1) + source);
    }

    /** Reads an optional bundle resource, returning null when it is absent. */
    private static String optionalRead(BundleResources resources, String path) {
        try {
            return resources.read(path);
        } catch (RuntimeException absent) {
            return null;
        }
    }
}

interface BundleResources { String read(String path); }

final class ClasspathBundleResources implements BundleResources {
    private final ClassLoader classLoader;
    ClasspathBundleResources(ClassLoader classLoader) { this.classLoader = classLoader; }
    @Override public String read(String path) {
        try (InputStream input = classLoader.getResourceAsStream("api-policy/" + path)) {
            if (input == null) throw new BundleValidationException("Missing bundle resource '" + path + "'");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BundleValidationException("Could not read bundle resource '" + path + "': " + e.getMessage());
        }
    }
}
