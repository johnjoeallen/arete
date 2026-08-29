package com.speculate.validation.policy;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import net.dublinux.speculate.validation.spi.Severity;
import net.dublinux.speculate.validation.spi.SpecFormat;
import net.dublinux.speculate.validation.spi.SpecInput;
import net.dublinux.speculate.validation.spi.SpecValidationPlugin;
import net.dublinux.speculate.validation.spi.RuleDocumentation;
import net.dublinux.speculate.validation.spi.RuleDocumentationProvider;
import net.dublinux.speculate.validation.spi.ValidationResult;
import net.dublinux.speculate.validation.spi.Violation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Set;

/** First working implementation of the bundled generic policy engine. */
public final class GenericPolicyValidationPlugin implements SpecValidationPlugin, RuleDocumentationProvider {
    private static final String DOCUMENTATION_BASE_URL = "http://localhost:6809/plugins/generic-policy/rules/";
    private static final System.Logger LOG = System.getLogger(GenericPolicyValidationPlugin.class.getName());
    private volatile PolicyBundle bundle;
    private final PolicyBundleLoader bundleLoader = new PolicyBundleLoader();
    private final GroovyDetectorRuntime groovyRuntime = new GroovyDetectorRuntime();
    private final StarlarkDetectorRuntime starlarkRuntime = new StarlarkDetectorRuntime();

    @Override public String getId() { return "generic-policy"; }
    @Override public String getName() { return "Speculate Policy Engine"; }
    @Override public String getVersion() { return "0.1.0-SNAPSHOT"; }

    @Override
    public Set<SpecFormat> getSupportedFormats() {
        return EnumSet.of(SpecFormat.OPENAPI3, SpecFormat.SWAGGER2);
    }

    @Override
    public List<String> getRuleSets() {
        return activeBundle().policies().keySet().stream().toList();
    }

    /** Detector language precedence used when nothing is configured. */
    static final List<String> DEFAULT_LANGUAGE_PRECEDENCE = List.of("starlark");

    @Override
    public synchronized void configure(Map<String, String> config) {
        List<String> precedence = resolveLanguagePrecedence(config);
        if (precedence.contains("groovy")) {
            LOG.log(System.Logger.Level.WARNING,
                    "Speculate policy engine: the Groovy detector runtime is enabled (detector-languages={0}). "
                            + "This runtime is UNSANDBOXED; only enable it for bundles you fully trust.", precedence);
        }
        bundle = bundleLoader.load(new ClasspathBundleResources(getClass().getClassLoader()),
                new PolicyBundleLoader.LoadOptions(precedence));
    }

    /**
     * Resolves the detector language precedence, in order of precedence:
     * the {@code detector-languages} plugin config key (comma-separated), the
     * {@code detector-language} key (a single language, added after Starlark),
     * then the matching {@code speculate.policy.*} system properties, then the
     * Starlark-only default.
     */
    private static List<String> resolveLanguagePrecedence(Map<String, String> config) {
        String list = configOrProperty(config, "detector-languages", "speculate.policy.detector-languages");
        if (list != null && !list.isBlank()) {
            List<String> parsed = new ArrayList<>();
            for (String token : list.split(",")) {
                String language = token.trim().toLowerCase();
                if (!language.isEmpty() && !parsed.contains(language)) parsed.add(language);
            }
            if (!parsed.isEmpty()) return List.copyOf(parsed);
        }
        String single = configOrProperty(config, "detector-language", "speculate.policy.detector-language");
        if (single != null && !single.isBlank()) {
            String language = single.trim().toLowerCase();
            if (language.equals("starlark")) return List.of("starlark");
            // A single non-Starlark language means "also allow this one", with
            // Starlark still preferred where a Starlark source exists.
            return List.of("starlark", language);
        }
        return DEFAULT_LANGUAGE_PRECEDENCE;
    }

    private static String configOrProperty(Map<String, String> config, String configKey, String propertyKey) {
        String value = config == null ? null : config.get(configKey);
        return value != null ? value : System.getProperty(propertyKey);
    }

    @Override
    public ValidationResult validate(SpecInput input) {
        PolicyBundle currentBundle;
        try {
            currentBundle = activeBundle();
        } catch (BundleValidationException e) {
            return ValidationResult.pluginError("Could not load generic policy bundle: " + e.getMessage());
        }
        SwaggerParseResult parsed = new OpenAPIV3Parser().readContents(input.getContent(), null, new ParseOptions());
        if (parsed.getOpenAPI() == null) {
            String detail = parsed.getMessages() == null ? "unknown parse error" : String.join("; ", parsed.getMessages());
            return ValidationResult.parseError("OpenAPI parsing failed: " + detail);
        }

        Policy policy = currentBundle.policyOrDefault(input.getRuleSet());
        Map<String, Object> api = OpenApiMapAdapter.toMap(parsed.getOpenAPI());
        List<Violation> violations = new ArrayList<>();
        double deductions = 0;
        boolean prohibitedMatched = false;
        int rulesEvaluated = 0;

        for (Map.Entry<String, PolicyDisposition> policyRule : policy.dispositions().entrySet()) {
            Rule rule = currentBundle.rules().get(policyRule.getKey());
            rulesEvaluated++;
            List<Occurrence> occurrences;
            try {
                Detector detector = currentBundle.detectors().get(rule.detector());
                if (detector == null) {
                    return ValidationResult.pluginError("Detector '" + rule.detector() + "' required by " + rule.id() + " is not available in this bundle");
                }
                Map<String, Object> parameters = new LinkedHashMap<>(rule.parameters());
                parameters.putAll(policyRule.getValue().parameters());
                Rule effectiveRule = new Rule(rule.id(), rule.title(), rule.category(), rule.detector(), rule.scope(), parameters, rule.documentationMarkdown());
                occurrences = "groovy".equals(detector.language())
                        ? groovyRuntime.execute(detector, api, effectiveRule)
                        : starlarkRuntime.execute(detector, api, effectiveRule);
            } catch (DetectorException e) {
                return ValidationResult.pluginError("Detector '" + rule.detector() + "' failed for " + rule.id() + ": " + e.getMessage());
            }
            if (occurrences.isEmpty()) continue;

            PolicyDisposition disposition = policyRule.getValue();
            if (disposition instanceof Deduction deduction) deductions += deduction.points();
            else prohibitedMatched = true;

            for (Occurrence occurrence : occurrences) {
                Violation.Builder violation = Violation.builder()
                        .ruleId(rule.id()).title(rule.title()).description(occurrence.message())
                        .severity(disposition instanceof Prohibited ? Severity.ERROR : Severity.WARNING)
                        .scoreImprovement(disposition instanceof Deduction deduction ? deduction.points() : 0)
                        .documentationUrl(DOCUMENTATION_BASE_URL + rule.id());
                if (occurrence.pointer() != null) violation.pointer(occurrence.pointer());
                if (occurrence.path() != null) violation.paths(List.of(occurrence.path()));
                violations.add(violation.build());
            }
        }

        double qualityScore = Math.max(0, 100 - deductions);
        double effectiveScore = prohibitedMatched ? 0 : qualityScore;
        return ValidationResult.builder().status(ValidationResult.Status.SUCCESS).violations(violations)
                .rulesEvaluatedCount(rulesEvaluated).overallScore(effectiveScore)
                .overallScoreWithoutBlockers(qualityScore).build();
    }

    private PolicyBundle activeBundle() {
        PolicyBundle current = bundle;
        if (current == null) {
            synchronized (this) {
                if (bundle == null) configure(Map.of());
                current = bundle;
            }
        }
        return current;
    }

    @Override
    public Optional<RuleDocumentation> getRuleDocumentation(String ruleId) {
        Rule rule = activeBundle().rules().get(ruleId);
        return rule == null ? Optional.empty() : Optional.of(new RuleDocumentation(rule.title(), interpolateDocumentation(rule.documentationMarkdown(), rule.parameters())));
    }

    /** Replaces {@code {{parameter-name}}} placeholders with declared rule parameters. */
    private static String interpolateDocumentation(String markdown, Map<String, Object> parameters) {
        String rendered = markdown;
        for (Map.Entry<String, Object> parameter : parameters.entrySet()) {
            rendered = rendered.replace("{{" + parameter.getKey() + "}}", String.valueOf(parameter.getValue()));
        }
        return rendered;
    }
}
