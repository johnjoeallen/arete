package net.dublinux.arete.validation.policy;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.swagger.v3.parser.util.DeserializationUtils;
import net.dublinux.arete.validation.spi.Severity;
import net.dublinux.arete.validation.spi.SpecFormat;
import net.dublinux.arete.validation.spi.SpecInput;
import net.dublinux.arete.validation.spi.SpecValidationPlugin;
import net.dublinux.arete.validation.spi.MatcherTestProvider;
import net.dublinux.arete.validation.spi.MatcherTestRequest;
import net.dublinux.arete.validation.spi.RuleDocumentation;
import net.dublinux.arete.validation.spi.RuleDocumentationProvider;
import net.dublinux.arete.validation.spi.ValidationResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Set;

/** First working implementation of the bundled generic policy engine. */
public final class PolicyBasedValidationPlugin implements SpecValidationPlugin, RuleDocumentationProvider, MatcherTestProvider {
    private static final String DOCUMENTATION_BASE_URL = "http://localhost:6809/plugins/generic-policy/rules/";
    private static final int MAX_YAML_CODE_POINTS = 50 * 1024 * 1024;
    private static final System.Logger LOG = System.getLogger(PolicyBasedValidationPlugin.class.getName());

    static {
        // The plugin has its own classloader, so the host application's
        // swagger-parser YAML configuration does not reach this parser.
        DeserializationUtils.getOptions().setMaxYamlCodePoints(MAX_YAML_CODE_POINTS);
    }

    private volatile PolicyBundle bundle;
    private final PolicyBundleLoader bundleLoader = new PolicyBundleLoader();
    private final GroovyMatcherEvaluator groovyRuntime = new GroovyMatcherEvaluator();
    private final DistillMatcherEvaluator distillRuntime = new DistillMatcherEvaluator();
    private volatile boolean forkRules;
    private volatile long forkRuleTimeoutMillis = 5000;

    @Override public String getId() { return "generic-policy"; }
    @Override public String getName() { return "Areté Policy Engine"; }
    @Override public String getVersion() { return "0.1.0-SNAPSHOT"; }

    @Override
    public Set<SpecFormat> getSupportedFormats() {
        return EnumSet.of(SpecFormat.OPENAPI3, SpecFormat.SWAGGER2);
    }

    @Override
    public List<String> getRuleSets() {
        return activeBundle().policies().keySet().stream().toList();
    }

    /** Matcher language precedence used when nothing is configured. */
    static final List<String> DEFAULT_LANGUAGE_PRECEDENCE = List.of("distill", "groovy");

    @Override
    public synchronized void configure(Map<String, String> config) {
        List<String> precedence = resolveLanguagePrecedence(config);
        bundle = bundleLoader.load(new ClasspathBundleResources(getClass().getClassLoader()),
                new PolicyBundleLoader.LoadOptions(precedence), loadUserPolicies(config));
        forkRules = booleanConfig(config, "fork-rules", "arete.policy.fork-rules", false);
        forkRuleTimeoutMillis = longConfig(config, "fork-rule-timeout-ms", "arete.policy.fork-rule-timeout-ms", 5000);
    }

    /**
     * Resolves the rule language precedence, in order of precedence:
     * the {@code rule-languages} plugin config key (comma-separated), the
     * {@code rule-language} key (a single language, appended to the
     * Distill/Groovy default), then the matching {@code arete.policy.*} system
     * properties, then the default.
     */
    private static List<String> resolveLanguagePrecedence(Map<String, String> config) {
        String list = configOrProperty(config, "rule-languages", "arete.policy.rule-languages");
        if (list != null && !list.isBlank()) {
            List<String> parsed = new ArrayList<>();
            for (String token : list.split(",")) {
                String language = token.trim().toLowerCase();
                if (!language.isEmpty() && !parsed.contains(language)) parsed.add(language);
            }
            if (!parsed.isEmpty()) return List.copyOf(parsed);
        }
        String single = configOrProperty(config, "rule-language", "arete.policy.rule-language");
        if (single != null && !single.isBlank()) {
            String language = single.trim().toLowerCase();
            if (DEFAULT_LANGUAGE_PRECEDENCE.contains(language)) return List.of(language);
            // A single language outside the default means "also allow this one",
            // with the default runtimes still preferred where a source exists.
            List<String> precedence = new ArrayList<>(DEFAULT_LANGUAGE_PRECEDENCE);
            precedence.add(language);
            return List.copyOf(precedence);
        }
        return DEFAULT_LANGUAGE_PRECEDENCE;
    }

    /**
     * Loads user-supplied policy documents from a directory outside the
     * bundled jar — every {@code *.md} file in it, in filename order. The
     * directory is the {@code policies-dir} plugin config key, else the
     * {@code arete.policy.policies-dir} system property, else
     * {@code ~/.arete/policies}. A missing directory yields no policies; an
     * unreadable file aborts the load, as a malformed bundle does.
     */
    private static List<PolicyBundleLoader.OverlayPolicy> loadUserPolicies(Map<String, String> config) {
        String configured = configOrProperty(config, "policies-dir", "arete.policy.policies-dir");
        Path dir = configured != null && !configured.isBlank()
                ? Path.of(configured.trim())
                : Path.of(System.getProperty("user.home", ""), ".arete", "policies");
        if (!Files.isDirectory(dir)) return List.of();

        List<PolicyBundleLoader.OverlayPolicy> policies = new ArrayList<>();
        try (var entries = Files.list(dir)) {
            List<Path> files = entries
                    .filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".md"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
            for (Path file : files) {
                policies.add(new PolicyBundleLoader.OverlayPolicy(
                        dir.getFileName() + "/" + file.getFileName(),
                        Files.readString(file, StandardCharsets.UTF_8)));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read policy directory " + dir, e);
        }
        if (!policies.isEmpty()) {
            LOG.log(System.Logger.Level.INFO, "Loaded {0} user policy file(s) from {1}", policies.size(), dir);
        }
        return policies;
    }

    private static String configOrProperty(Map<String, String> config, String configKey, String propertyKey) {
        String value = config == null ? null : config.get(configKey);
        return value != null ? value : System.getProperty(propertyKey);
    }

    private static boolean booleanConfig(Map<String, String> config, String configKey, String propertyKey, boolean fallback) {
        String value = configOrProperty(config, configKey, propertyKey);
        if (value == null || value.isBlank()) return fallback;
        if (value.equalsIgnoreCase("true")) return true;
        if (value.equalsIgnoreCase("false")) return false;
        throw new IllegalArgumentException(configKey + " must be true or false");
    }

    private static long longConfig(Map<String, String> config, String configKey, String propertyKey, long fallback) {
        String value = configOrProperty(config, configKey, propertyKey);
        if (value == null || value.isBlank()) return fallback;
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 1) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(configKey + " must be a positive integer");
        }
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
        Map<String, Object> api = OpenApiMapAdapter.toMap(parsed.getOpenAPI(), parsed.getMessages(), input.getContent());
        List<net.dublinux.arete.validation.spi.Diagnostic> diagnostics = new ArrayList<>();
        double deductions = 0;
        boolean prohibitedMatched = false;
        int rulesEvaluated = 0;

        for (Map.Entry<String, PolicyDisposition> policyRule : policy.dispositions().entrySet()) {
            PolicyRule rule = currentBundle.rules().get(policyRule.getKey());
            rulesEvaluated++;
            List<net.dublinux.arete.validation.policy.Diagnostic> matches;
            try {
                Matcher matcher = currentBundle.matchers().get(rule.matcherId());
                if (matcher == null) {
                    return ValidationResult.pluginError("Matcher '" + rule.matcherId() + "' required by " + rule.id() + " is not available in this bundle");
                }
                Map<String, Object> parameters = new LinkedHashMap<>(rule.parameters());
                parameters.putAll(policyRule.getValue().parameters());
                PolicyRule effectiveRule = new PolicyRule(rule.id(), rule.title(), rule.category(), rule.matcherId(), rule.scope(), parameters, rule.documentationMarkdown());
                matches = forkRules
                        ? new ForkedMatcherEvaluator(forkRuleTimeoutMillis).execute(matcher, api, effectiveRule)
                        : switch (matcher.language()) {
                            case "groovy" -> groovyRuntime.execute(matcher, api, effectiveRule);
                            case "distill" -> distillRuntime.execute(matcher, api, effectiveRule);
                            default -> throw new MatcherEvaluationException("Unsupported matcher language: " + matcher.language());
                        };
            } catch (MatcherEvaluationException e) {
                return ValidationResult.pluginError("Matcher '" + rule.matcherId() + "' failed for " + rule.id() + ": " + e.getMessage());
            }
            if (matches.isEmpty()) continue;

            PolicyDisposition disposition = policyRule.getValue();
            if (disposition instanceof Deduction deduction) deductions += deduction.points();
            else prohibitedMatched = true;

            for (net.dublinux.arete.validation.policy.Diagnostic match : matches) {
                net.dublinux.arete.validation.spi.Diagnostic.Builder diagnostic = net.dublinux.arete.validation.spi.Diagnostic.builder()
                        .ruleId(rule.id()).title(rule.title()).description(match.message())
                        .severity(disposition instanceof Prohibited ? Severity.ERROR : Severity.WARNING)
                        .scoreImprovement(disposition instanceof Deduction deduction ? deduction.points() : 0)
                        .documentationUrl(DOCUMENTATION_BASE_URL + rule.id());
                if (match.pointer() != null) diagnostic.pointer(match.pointer());
                if (match.path() != null) diagnostic.paths(List.of(match.path()));
                diagnostics.add(diagnostic.build());
            }
        }

        double qualityScore = Math.max(0, 100 - deductions);
        double effectiveScore = prohibitedMatched ? 0 : qualityScore;
        return ValidationResult.builder().status(ValidationResult.Status.SUCCESS).diagnostics(diagnostics)
                .rulesEvaluatedCount(rulesEvaluated).overallScore(effectiveScore)
                .overallScoreWithoutBlockers(qualityScore).build();
    }

    @Override
    public ValidationResult testMatcher(MatcherTestRequest request) {
        try {
            SwaggerParseResult parsed = new OpenAPIV3Parser().readContents(request.spec(), null, new ParseOptions());
            if (parsed.getOpenAPI() == null) {
                String detail = parsed.getMessages() == null ? "unknown parse error" : String.join("; ", parsed.getMessages());
                return ValidationResult.parseError("OpenAPI parsing failed: " + detail);
            }
            Matcher matcher = new Matcher(request.matcherId(), request.language(), request.source(),
                    List.of(request.scope()), Map.of());
            switch (request.language()) {
                case "distill" -> distillRuntime.validate(matcher);
                case "groovy" -> groovyRuntime.validate(matcher);
                default -> throw new MatcherEvaluationException("Unsupported matcher language: " + request.language());
            }
            Map<String, Object> api = OpenApiMapAdapter.toMap(parsed.getOpenAPI(), parsed.getMessages(), request.spec());
            PolicyRule rule = new PolicyRule(request.matcherId(), request.matcherId(), "Matcher test",
                    request.matcherId(), request.scope(), request.parameters(), "");
            List<Diagnostic> matches = switch (request.language()) {
                case "distill" -> distillRuntime.execute(matcher, api, rule);
                case "groovy" -> groovyRuntime.execute(matcher, api, rule);
                default -> throw new MatcherEvaluationException("Unsupported matcher language: " + request.language());
            };
            List<net.dublinux.arete.validation.spi.Diagnostic> diagnostics = matches.stream().map(match -> {
                net.dublinux.arete.validation.spi.Diagnostic.Builder diagnostic = net.dublinux.arete.validation.spi.Diagnostic.builder()
                        .ruleId(request.matcherId()).title(request.matcherId()).description(match.message())
                        .severity(Severity.WARNING);
                if (match.pointer() != null) diagnostic.pointer(match.pointer());
                if (match.path() != null) diagnostic.paths(List.of(match.path()));
                return diagnostic.build();
            }).toList();
            return ValidationResult.success(diagnostics, 1);
        } catch (MatcherEvaluationException | BundleValidationException e) {
            return ValidationResult.pluginError(e.getMessage());
        } catch (RuntimeException e) {
            return ValidationResult.pluginError("Matcher test failed: " + e.getMessage());
        }
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
    public Optional<RuleDocumentation> getRuleDocumentation(String matcherId) {
        PolicyRule rule = activeBundle().rules().get(matcherId);
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
