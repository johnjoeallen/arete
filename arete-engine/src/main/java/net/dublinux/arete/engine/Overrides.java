package net.dublinux.arete.engine;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * A team's deliberate, reviewed deviations from a policy, read from an {@code .arete.yaml} that sits
 * next to its specs:
 *
 * <pre>{@code
 * policy: Enterprise Grade          # optional: the policy to use for this folder
 * overrides:
 *   STATUS003:
 *     reason: Our gateway answers 403 for a missing token, by design.
 *     disable: true
 *   PAGE004:
 *     reason: Reporting endpoints page in thousands.
 *     points: 1
 *     parameters: { maximum: 1000 }
 * }</pre>
 *
 * <p>Every override states a {@code reason}. It can {@code disable} the rule, change its {@code points},
 * or change its {@code parameters}. A rule the policy marks {@code locked} cannot be overridden, and an
 * override of a rule the bundle does not have is an error, so a typo cannot silently do nothing.
 */
public final class Overrides {
    /** One rule's override. {@code points} and {@code parameters} may be null. */
    public record RuleOverride(String ruleId, String reason, boolean disabled, Double points, Map<String, Object> parameters) { }

    private static final Overrides NONE = new Overrides(null, Map.of());

    private final String policy;
    private final Map<String, RuleOverride> rules;

    private Overrides(String policy, Map<String, RuleOverride> rules) {
        this.policy = policy;
        this.rules = Collections.unmodifiableMap(new LinkedHashMap<>(rules));
    }

    public static Overrides none() { return NONE; }

    /** Parses the text of an {@code .arete.yaml}. */
    public static Overrides parse(String text) {
        if (text == null || text.isBlank()) return NONE;
        Object document;
        try {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setMaxAliasesForCollections(20);
            document = new Yaml(new SafeConstructor(options)).load(text);
        } catch (RuntimeException e) {
            throw new BundleValidationException(".arete.yaml: not valid YAML (" + e.getMessage() + ")");
        }
        if (document == null) return NONE;
        Map<String, Object> top = mapOf(".arete.yaml", document);
        reject(".arete.yaml", top, Set.of("policy", "overrides"));
        String policy = null;
        if (top.containsKey("policy")) {
            if (!(top.get("policy") instanceof String name) || name.isBlank()) throw new BundleValidationException(".arete.yaml: policy must be a name");
            policy = name.strip();
        }
        Map<String, RuleOverride> rules = new LinkedHashMap<>();
        if (top.containsKey("overrides")) {
            for (Map.Entry<String, Object> entry : mapOf(".arete.yaml: overrides", top.get("overrides")).entrySet()) {
                String where = ".arete.yaml: overrides." + entry.getKey();
                Map<String, Object> body = mapOf(where, entry.getValue());
                reject(where, body, Set.of("reason", "disable", "points", "parameters"));
                if (!(body.get("reason") instanceof String reason) || reason.isBlank()) {
                    throw new BundleValidationException(where + " needs a reason");
                }
                boolean disabled = false;
                if (body.containsKey("disable")) {
                    if (!(body.get("disable") instanceof Boolean flag)) throw new BundleValidationException(where + ".disable must be true or false");
                    disabled = flag;
                }
                Double points = null;
                if (body.containsKey("points")) {
                    if (!(body.get("points") instanceof Number number) || number.doubleValue() < 0 || number.doubleValue() > 100) {
                        throw new BundleValidationException(where + ".points must be a number from 0 to 100");
                    }
                    points = number.doubleValue();
                }
                Map<String, Object> parameters = body.containsKey("parameters") ? mapOf(where + ".parameters", body.get("parameters")) : null;
                if (!disabled && points == null && parameters == null) {
                    throw new BundleValidationException(where + " must disable the rule or change its points or parameters");
                }
                if (disabled && (points != null || parameters != null)) {
                    throw new BundleValidationException(where + " disables the rule, so points and parameters have no effect");
                }
                rules.put(entry.getKey(), new RuleOverride(entry.getKey(), reason.strip(), disabled, points, parameters));
            }
        }
        return new Overrides(policy, rules);
    }

    /**
     * The nearest {@code .arete.yaml} from the spec's folder up to {@code root} (inclusive), parsed; none if there is no
     * such file. The nearest wins outright: files are not merged.
     */
    public static Overrides discover(java.nio.file.Path spec, java.nio.file.Path root) {
        java.nio.file.Path top = root.toAbsolutePath().normalize();
        for (java.nio.file.Path dir = spec.toAbsolutePath().normalize().getParent(); dir != null && dir.startsWith(top); dir = dir.getParent()) {
            java.nio.file.Path file = dir.resolve(".arete.yaml");
            if (java.nio.file.Files.isRegularFile(file)) {
                try {
                    return parse(java.nio.file.Files.readString(file, java.nio.charset.StandardCharsets.UTF_8));
                } catch (java.io.IOException e) {
                    throw new BundleValidationException(file + " could not be read: " + e.getMessage());
                }
            }
        }
        return NONE;
    }

    /** The policy the folder asks for, or null. */
    public String policy() { return policy; }

    public Map<String, RuleOverride> rules() { return rules; }

    public boolean isEmpty() { return rules.isEmpty() && policy == null; }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapOf(String where, Object value) {
        if (!(value instanceof Map<?, ?> map)) throw new BundleValidationException(where + " must be a mapping");
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) copy.put(String.valueOf(entry.getKey()), entry.getValue());
        return copy;
    }

    private static void reject(String where, Map<String, Object> map, Set<String> allowed) {
        for (String key : map.keySet()) {
            if (!allowed.contains(key)) throw new BundleValidationException(where + " has unknown key '" + key + "'");
        }
    }
}
