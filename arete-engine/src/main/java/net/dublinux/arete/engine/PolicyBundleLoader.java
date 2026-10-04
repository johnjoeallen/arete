package net.dublinux.arete.engine;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loads and validates every declarative resource and rule reference. */
final class PolicyBundleLoader {
    private final Yaml yaml;
    private final DistillMatcherEvaluator distillRuntime = new DistillMatcherEvaluator();

    /** The only matcher language: every matcher is a {@code Matcher.distill} beside its {@code Matcher.md}. */
    private static final String MATCHER_SOURCE = "Matcher.distill";

    PolicyBundleLoader() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(20);
        yaml = new Yaml(new SafeConstructor(options));
    }

    /**
     * A policy document supplied from outside the classpath bundle — a file
     * under {@code ~/.arete/policies/}, say. {@code name} is used only in
     * scoring error messages.
     */
    record OverlayPolicy(String name, String content) { }

    PolicyBundle load(BundleResources resources) {
        return load(resources, null, List.of());
    }

    PolicyBundle load(BundleResources resources, List<OverlayPolicy> overlayPolicies) {
        return load(resources, null, overlayPolicies);
    }

    /**
     * Loads one bundle. With a {@code base}, the new bundle is layered on it: its rules, matchers and
     * policies add to the base or replace the base's entry with the same id, may refer to anything the
     * base defines, and may leave out any section it does not need.
     */
    PolicyBundle load(BundleResources resources, PolicyBundle base, List<OverlayPolicy> overlayPolicies) {
        Map<String, Object> manifest = yamlMap("PolicyBundle.yaml", resources.read("PolicyBundle.yaml"));
        rejectUnknown("PolicyBundle.yaml", manifest, Set.of("formatVersion", "bundleId", "bundleVersion", "rules", "policies", "matchers"));
        if (!Integer.valueOf(1).equals(manifest.get("formatVersion"))) throw new BundleValidationException("PolicyBundle.yaml: formatVersion must be 1");
        Map<String, String> rulePaths = stringMap("PolicyBundle.yaml", "rules", base != null && manifest.get("rules") == null ? Map.of() : manifest.get("rules"));
        Map<String, String> policyPaths = stringMap("PolicyBundle.yaml", "policies", base != null && manifest.get("policies") == null ? Map.of() : manifest.get("policies"));
        Map<String, String> matcherPaths = stringMap("PolicyBundle.yaml", "matchers", base != null && manifest.get("matchers") == null ? Map.of() : manifest.get("matchers"));
        if (base == null && (rulePaths.isEmpty() || policyPaths.isEmpty() || matcherPaths.isEmpty())) throw new BundleValidationException("PolicyBundle.yaml: rules, policies, and matchers must not be empty");

        Map<String, Matcher> matchers = new LinkedHashMap<>();
        if (base != null) matchers.putAll(base.matchers());
        for (Map.Entry<String, String> entry : matcherPaths.entrySet()) {
            String descriptorPath = safePath("PolicyBundle.yaml", entry.getValue());
            Matcher descriptor = parseRuleDefinition(descriptorPath, resources.read(descriptorPath));
            if (!entry.getKey().equals(descriptor.id())) throw new BundleValidationException(descriptorPath + ": manifest rule id does not match descriptor id");

            String source = optionalRead(resources, siblingPath(descriptorPath, MATCHER_SOURCE));
            if (source == null) throw new BundleValidationException(descriptorPath + ": no " + MATCHER_SOURCE + " beside it");
            Matcher matcher = new Matcher(descriptor.id(), "distill", source, descriptor.scopes(), descriptor.parameters());
            distillRuntime.validate(matcher);
            matchers.put(matcher.id(), matcher);
        }

        Map<String, PolicyRule> rules = new LinkedHashMap<>();
        if (base != null) rules.putAll(base.rules());
        for (Map.Entry<String, String> entry : rulePaths.entrySet()) {
            String path = safePath("PolicyBundle.yaml", entry.getValue());
            PolicyRule rule = parseRule(path, resources.read(path));
            if (!entry.getKey().equals(rule.id())) throw new BundleValidationException(path + ": manifest rule id does not match rule id");
            Matcher matcher = matchers.get(rule.matcherId());
            // A catalogue can document rules before their reusable rule
            // ships. Keep those rules loadable, but only validate parameters
            // for rule capabilities that are currently available.
            if (matcher != null) {
                validateRule(path, rule, matcher);
                rule = coerceListParameters(rule, matcher);
            }
            rules.put(rule.id(), rule);
        }

        Map<String, Policy> policies = new LinkedHashMap<>();
        if (base != null) policies.putAll(base.policies());
        for (Map.Entry<String, String> entry : policyPaths.entrySet()) {
            String path = safePath("PolicyBundle.yaml", entry.getValue());
            Policy policy = parsePolicy(path, resources.read(path), rules, matchers);
            if (!entry.getKey().equals(policy.id())) throw new BundleValidationException(path + ": manifest policy id does not match policy id");
            for (String matcherId : policy.dispositions().keySet()) if (!rules.containsKey(matcherId)) throw new BundleValidationException(path + ": unknown policy rule '" + matcherId + "'");
            policies.put(policy.id(), policy);
        }

        return withOverlays(new PolicyBundle(rules, policies, matchers, optionalString(manifest.get("bundleId")), optionalString(manifest.get("bundleVersion"))), overlayPolicies);
    }

    /**
     * Adds policy documents from outside a bundle (a file under {@code ~/.arete/policies/}, say). They are
     * parsed against the bundle's rules and matchers and added last, so one that reuses a bundled policy
     * id deliberately replaces it.
     */
    PolicyBundle withOverlays(PolicyBundle bundle, List<OverlayPolicy> overlays) {
        if (overlays.isEmpty()) return bundle;
        Map<String, Policy> policies = new LinkedHashMap<>(bundle.policies());
        for (OverlayPolicy overlay : overlays) {
            Policy policy = parsePolicy(overlay.name(), overlay.content(), bundle.rules(), bundle.matchers());
            for (String ruleId : policy.dispositions().keySet()) if (!bundle.rules().containsKey(ruleId)) throw new BundleValidationException(overlay.name() + ": unknown policy rule '" + ruleId + "'");
            policies.put(policy.id(), policy);
        }
        return new PolicyBundle(bundle.rules(), policies, bundle.matchers(), bundle.bundleId(), bundle.bundleVersion());
    }

    private static String optionalString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Matcher parseRuleDefinition(String path, String content) {
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
            if (("string".equals(type) || "integer".equals(type) || "boolean".equals(type) || "list".equals(type)) && !values.isEmpty()) {
                throw new BundleValidationException(path + ": " + type + " parameter '" + entry.getKey() + "' must not declare values");
            }
            if (!Set.of("enum", "string", "integer", "boolean", "list").contains(type)) {
                throw new BundleValidationException(path + ": unsupported parameter type '" + type + "'");
            }
            parameters.put(entry.getKey(), new ParameterDefinition(type, required, values));
        }
        return new Matcher(requiredString(path, "id", data.get("id")), requiredString(path, "language", data.get("language")), requiredString(path, "source", data.get("source")), stringList(path, "scopes", data.get("scopes")), Map.copyOf(parameters));
    }

    private PolicyRule parseRule(String path, String content) {
        Map<String, Object> data = frontMatter(path, content);
        rejectUnknown(path, data, Set.of("id", "category", "matcher", "scope", "parameters"));
        Object rawParameters = data.get("parameters");
        Map<String, Object> parameters = rawParameters == null ? Map.of() : map(path, "parameters", rawParameters);
        return new PolicyRule(requiredString(path, "id", data.get("id")), title(path, content), requiredString(path, "category", data.get("category")), requiredString(path, "matcher", data.get("matcher")), requiredString(path, "scope", data.get("scope")), Map.copyOf(parameters), markdownBody(path, content));
    }

    private Policy parsePolicy(String path, String content, Map<String, PolicyRule> rules, Map<String, Matcher> matchers) {
        Map<String, Object> data = frontMatter(path, content);
        rejectUnknown(path, data, Set.of("id", "format", "rules", "scoring", "passingScore", "grades"));
        int format = 1;
        if (data.containsKey("format")) {
            if (!(data.get("format") instanceof Integer declared) || (declared != 1 && declared != 2)) throw new BundleValidationException(path + ": format must be 1 or 2");
            format = declared;
        }
        Set<String> locked = new java.util.LinkedHashSet<>();
        String scoreLevel = data.containsKey("scoring") ? scoreLevel(path, data.get("scoring")) : null;
        Double passingScore = data.containsKey("passingScore") ? score(path, "passingScore", data.get("passingScore")) : null;
        Map<String, Double> grades = data.containsKey("grades")
                ? grades(path, data.get("grades"))
                : passingScore != null ? passingScoreGrades(passingScore)
                : DEFAULT_GRADES;
        Map<String, PolicyDisposition> dispositions = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : map(path, "rules", data.get("rules")).entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Number number && validPoints(number)) {
                dispositions.put(entry.getKey(), new Deduction(number.doubleValue()));
            } else if ("PROHIBITED".equals(value)) {
                dispositions.put(entry.getKey(), new Prohibited());
            } else if (value instanceof Map<?, ?> raw) {
                Map<String, Object> declaration = map(path, "rules." + entry.getKey(), raw);
                rejectUnknown(path, declaration, Set.of("points", "parameters", "locked", "expect", "per-match", "max", "tiers"));
                if (declaration.containsKey("locked")) {
                    if (!(declaration.get("locked") instanceof Boolean flag)) throw new BundleValidationException(path + ": " + entry.getKey() + ".locked must be true or false");
                    if (flag) locked.add(entry.getKey());
                }
                for (String extended : List.of("expect", "per-match", "max", "tiers")) {
                    if (declaration.containsKey(extended) && format < 2) {
                        throw new BundleValidationException(path + ": " + entry.getKey() + "." + extended + " needs format: 2 in the policy's front matter");
                    }
                }
                Object points = declaration.get("points");
                Map<String, Object> overrides = declaration.containsKey("parameters")
                        ? map(path, "rules." + entry.getKey() + ".parameters", declaration.get("parameters"))
                        : Map.of();
                PolicyRule rule = rules.get(entry.getKey());
                Matcher matcher = rule == null ? null : matchers.get(rule.matcherId());
                if (matcher != null) {
                    validateParameterOverrides(path, entry.getKey(), overrides, matcher);
                    overrides = coerceListValues(overrides, matcher);
                }
                boolean expectMatch = false;
                if (declaration.containsKey("expect")) {
                    Object expect = declaration.get("expect");
                    if ("match".equals(expect)) expectMatch = true;
                    else if (!"no-match".equals(expect)) throw new BundleValidationException(path + ": " + entry.getKey() + ".expect must be match or no-match");
                }
                String where = path + ": " + entry.getKey();
                int ways = (declaration.containsKey("points") ? 1 : 0) + (declaration.containsKey("per-match") ? 1 : 0) + (declaration.containsKey("tiers") ? 1 : 0);
                if (ways != 1) throw new BundleValidationException(where + " needs exactly one of points, per-match or tiers");
                if (declaration.containsKey("max") && !declaration.containsKey("per-match")) throw new BundleValidationException(where + ".max only goes with per-match");
                if (declaration.containsKey("per-match")) {
                    if (!(declaration.get("per-match") instanceof Number each) || !(each.doubleValue() > 0) || !validPoints(each)) {
                        throw new BundleValidationException(where + ".per-match must be a number above 0 and up to 100");
                    }
                    Double max = null;
                    if (declaration.containsKey("max")) {
                        if (!(declaration.get("max") instanceof Number cap) || !(cap.doubleValue() > 0) || !validPoints(cap)) {
                            throw new BundleValidationException(where + ".max must be a number above 0 and up to 100");
                        }
                        max = cap.doubleValue();
                    }
                    dispositions.put(entry.getKey(), new Graduated(each(declaration.get("per-match")), max, List.of(), overrides, expectMatch));
                } else if (declaration.containsKey("tiers")) {
                    dispositions.put(entry.getKey(), new Graduated(0, null, tiers(where, declaration.get("tiers")), overrides, expectMatch));
                } else if ("PROHIBITED".equals(points)) {
                    dispositions.put(entry.getKey(), new Prohibited(overrides, expectMatch));
                } else if (points instanceof Number number && validPoints(number)) {
                    dispositions.put(entry.getKey(), new Deduction(number.doubleValue(), overrides, expectMatch));
                } else {
                    throw new BundleValidationException(path + ": " + entry.getKey() + ".points must be a number from 0 to 100 or PROHIBITED");
                }
            } else throw new BundleValidationException(path + ": " + entry.getKey() + " must be a number, PROHIBITED, or a declaration with points and parameters");
        }
        return new Policy(requiredString(path, "id", data.get("id")), dispositions, scoreLevel, passingScore, grades, locked);
    }

    private static double each(Object value) { return ((Number) value).doubleValue(); }

    /** {@code tiers:} a map from a count to the points charged at that count or more, kept in ascending order. */
    private static List<Tier> tiers(String where, Object value) {
        if (!(value instanceof Map<?, ?> raw) || raw.isEmpty()) throw new BundleValidationException(where + ".tiers must be a map from a count to points");
        java.util.TreeMap<Integer, Double> sorted = new java.util.TreeMap<>();
        for (Map.Entry<?, ?> tier : raw.entrySet()) {
            int count;
            try {
                count = Integer.parseInt(String.valueOf(tier.getKey()).strip());
            } catch (NumberFormatException e) {
                throw new BundleValidationException(where + ".tiers has the key '" + tier.getKey() + "', which is not a count");
            }
            if (count < 1) throw new BundleValidationException(where + ".tiers keys must be 1 or more");
            if (!(tier.getValue() instanceof Number points) || !validPoints(points)) {
                throw new BundleValidationException(where + ".tiers." + count + " must be a number from 0 to 100");
            }
            if (sorted.put(count, points.doubleValue()) != null) throw new BundleValidationException(where + ".tiers has " + count + " twice");
        }
        List<Tier> tiers = new ArrayList<>();
        sorted.forEach((count, points) -> tiers.add(new Tier(count, points)));
        return tiers;
    }

    private static double score(String path, String field, Object value) {
        if (value instanceof Number number && Double.isFinite(number.doubleValue())
                && number.doubleValue() >= 0 && number.doubleValue() <= 100) {
            return number.doubleValue();
        }
        throw new BundleValidationException(path + ": " + field + " must be a number from 0 to 100");
    }

    /** {@code grades:} — an ordered {label -> min score} map, kept in declaration order (highest band first). */
    private static Map<String, Double> grades(String path, Object value) {
        Map<String, Double> out = new LinkedHashMap<>();
        double previous = Double.MAX_VALUE;
        for (Map.Entry<String, Object> entry : map(path, "grades", value).entrySet()) {
            double threshold = score(path, "grades." + entry.getKey(), entry.getValue());
            if (threshold > previous) {
                throw new BundleValidationException(path + ": grades must be listed from the highest threshold down");
            }
            previous = threshold;
            out.put(entry.getKey(), threshold);
        }
        if (out.isEmpty()) throw new BundleValidationException(path + ": grades must not be empty");
        return out;
    }

    /** The grade bands a policy gets when it declares neither {@code grades} nor {@code passingScore}. */
    private static final Map<String, Double> DEFAULT_GRADES;
    static {
        Map<String, Double> g = new LinkedHashMap<>();
        g.put("A", 90.0);
        g.put("B", 80.0);
        g.put("C", 70.0);
        g.put("D", 60.0);
        DEFAULT_GRADES = Collections.unmodifiableMap(g);
    }

    /**
     * When a policy sets a {@code passingScore} but no explicit {@code grades},
     * {@code C} is the pass mark and {@code A}/{@code B} space evenly above it
     * with {@code D} below, so a passing score always earns a grade.
     */
    private static Map<String, Double> passingScoreGrades(double passingScore) {
        double headroom = 100 - passingScore;
        Map<String, Double> out = new LinkedHashMap<>();
        out.put("A", round1(passingScore + headroom * 2 / 3));
        out.put("B", round1(passingScore + headroom / 3));
        out.put("C", round1(passingScore));
        out.put("D", round1(Math.max(0, passingScore * 0.8)));
        return out;
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    /** Validates and normalises a policy's {@code scoring:} value: {@code blocker | error | score<NN}. */
    private static String scoreLevel(String path, Object value) {
        if (!(value instanceof String raw) || raw.isBlank()) {
            throw new BundleValidationException(path + ": scoring must be 'blocker', 'error', or 'score<NN'");
        }
        String level = raw.trim();
        if (level.equals("blocker") || level.equals("error")) {
            return level;
        }
        if (level.startsWith("score<")) {
            try {
                double bar = Double.parseDouble(level.substring("score<".length()).trim());
                if (bar >= 0 && bar <= 100) {
                    return "score<" + (bar == Math.rint(bar) ? Long.toString((long) bar) : Double.toString(bar));
                }
            } catch (NumberFormatException ignored) {
                // fall through to the error below
            }
        }
        throw new BundleValidationException(path + ": scoring '" + raw
                + "' must be 'blocker', 'error', or 'score<NN' with NN from 0 to 100");
    }

    private static boolean validPoints(Number number) {
        return Double.isFinite(number.doubleValue()) && number.doubleValue() >= 0 && number.doubleValue() <= 100;
    }

    static void validateParameterOverrides(String path, String matcherId, Map<String, Object> overrides, Matcher rule) {
        for (Map.Entry<String, Object> parameter : overrides.entrySet()) {
            ParameterDefinition definition = rule.parameters().get(parameter.getKey());
            if (definition == null) throw new BundleValidationException(path + ": " + matcherId + " overrides unknown parameter '" + parameter.getKey() + "'");
            if (!validParameterValue(definition, parameter.getValue())) {
                throw new BundleValidationException(path + ": " + matcherId + " has invalid " + definition.type() + " override for parameter '" + parameter.getKey() + "'");
            }
        }
    }

    private static void validateRule(String path, PolicyRule policyRule, Matcher matcher) {
        if (!matcher.scopes().contains(policyRule.scope())) throw new BundleValidationException(path + ": scope '" + policyRule.scope() + "' is not supported by rule '" + matcher.id() + "'");
        for (Map.Entry<String, Object> parameter : policyRule.parameters().entrySet()) {
            ParameterDefinition definition = matcher.parameters().get(parameter.getKey());
            if (definition == null) throw new BundleValidationException(path + ": unknown parameter '" + parameter.getKey() + "' for rule '" + matcher.id() + "'");
            if (!validParameterValue(definition, parameter.getValue())) {
                throw new BundleValidationException(path + ": invalid " + definition.type() + " value for parameter '" + parameter.getKey() + "'");
            }
        }
        for (Map.Entry<String, ParameterDefinition> parameter : matcher.parameters().entrySet()) {
            if (parameter.getValue().required() && !policyRule.parameters().containsKey(parameter.getKey())) {
                throw new BundleValidationException(path + ": missing required parameter '" + parameter.getKey() + "' for rule '" + matcher.id() + "'");
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
            // A YAML list, or a comma-separated string the loader splits (see coerceListParameters).
            case "list" -> value instanceof List<?> || (value instanceof String text && !text.isBlank());
            default -> false; // parseRule rejects unknown types; retain defensive behaviour here.
        };
    }

    private static PolicyRule coerceListParameters(PolicyRule rule, Matcher matcher) {
        Map<String, Object> coerced = coerceListValues(rule.parameters(), matcher);
        return coerced == rule.parameters() ? rule
                : new PolicyRule(rule.id(), rule.title(), rule.category(), rule.matcherId(), rule.scope(),
                        Map.copyOf(coerced), rule.documentationMarkdown());
    }

    /**
     * Replaces any {@code list}-typed parameter given as a comma-separated
     * string with a trimmed, empty-dropped {@code List<String>}, so a matcher
     * always sees a list for that parameter. Returns the input map unchanged
     * when there is nothing to coerce.
     */
    static Map<String, Object> coerceListValues(Map<String, Object> parameters, Matcher matcher) {
        Map<String, Object> coerced = null;
        for (Map.Entry<String, Object> parameter : parameters.entrySet()) {
            ParameterDefinition definition = matcher.parameters().get(parameter.getKey());
            if (definition != null && "list".equals(definition.type()) && parameter.getValue() instanceof String csv) {
                if (coerced == null) coerced = new LinkedHashMap<>(parameters);
                List<String> items = new ArrayList<>();
                for (String part : csv.split(",")) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) items.add(trimmed);
                }
                coerced.put(parameter.getKey(), List.copyOf(items));
            }
        }
        return coerced == null ? parameters : coerced;
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
    private final String root;

    ClasspathBundleResources(ClassLoader classLoader) { this(classLoader, "api-policy"); }

    ClasspathBundleResources(ClassLoader classLoader, String root) {
        this.classLoader = classLoader;
        this.root = root.endsWith("/") ? root : root + "/";
    }

    @Override public String read(String path) {
        try (InputStream input = classLoader.getResourceAsStream(root + path)) {
            if (input == null) throw new BundleValidationException("Missing bundle resource '" + path + "'");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new BundleValidationException("Could not read bundle resource '" + path + "': " + e.getMessage());
        }
    }
}

/** A bundle laid out as a directory. A path that leaves the directory is refused. */
final class DirectoryBundleResources implements BundleResources {
    private final java.nio.file.Path root;

    DirectoryBundleResources(java.nio.file.Path root) { this.root = root.toAbsolutePath().normalize(); }

    @Override public String read(String path) {
        java.nio.file.Path file = root.resolve(path).normalize();
        if (!file.startsWith(root)) throw new BundleValidationException("Bundle resource '" + path + "' is outside the bundle");
        try {
            return java.nio.file.Files.readString(file, StandardCharsets.UTF_8);
        } catch (java.nio.file.NoSuchFileException e) {
            throw new BundleValidationException("Missing bundle resource '" + path + "'");
        } catch (IOException e) {
            throw new BundleValidationException("Could not read bundle resource '" + path + "': " + e.getMessage());
        }
    }
}

/** A bundle read from a zip archive into memory, with limits so a hostile archive cannot exhaust it. */
final class ZipBundleResources implements BundleResources {
    static final int MAX_ENTRIES = 5_000;
    static final long MAX_ENTRY_BYTES = 5L * 1024 * 1024;
    static final long MAX_TOTAL_BYTES = 50L * 1024 * 1024;

    private final Map<String, String> files;

    private ZipBundleResources(Map<String, String> files) { this.files = files; }

    static ZipBundleResources of(byte[] archive, String origin) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        long total = 0;
        try (java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(archive))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                if (entries.size() >= MAX_ENTRIES) throw new BundleValidationException(origin + ": archive has too many entries");
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zip.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                    total += read;
                    if (out.size() > MAX_ENTRY_BYTES || total > MAX_TOTAL_BYTES) {
                        throw new BundleValidationException(origin + ": archive is too large to be a policy bundle");
                    }
                }
                entries.put(entry.getName().replace('\\', '/'), out.toByteArray());
            }
        } catch (IOException e) {
            throw new BundleValidationException(origin + ": not a readable zip archive (" + e.getMessage() + ")");
        }
        // The manifest may sit at the archive root or inside one folder (policy-2.3.1/PolicyBundle.yaml).
        String prefix = null;
        for (String name : entries.keySet()) {
            if (name.equals("PolicyBundle.yaml")) { prefix = ""; break; }
            if (name.endsWith("/PolicyBundle.yaml") && (prefix == null || name.length() < prefix.length() + "PolicyBundle.yaml".length())) {
                prefix = name.substring(0, name.length() - "PolicyBundle.yaml".length());
            }
        }
        if (prefix == null) throw new BundleValidationException(origin + ": no PolicyBundle.yaml in the archive");
        Map<String, String> files = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                files.put(entry.getKey().substring(prefix.length()), new String(entry.getValue(), StandardCharsets.UTF_8));
            }
        }
        return new ZipBundleResources(files);
    }

    @Override public String read(String path) {
        String content = files.get(path);
        if (content == null) throw new BundleValidationException("Missing bundle resource '" + path + "'");
        return content;
    }
}
