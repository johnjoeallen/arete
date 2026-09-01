package net.dublinux.arete.scoring.policy;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.swagger.v3.parser.util.DeserializationUtils;
import net.dublinux.arete.scoring.spi.Severity;
import net.dublinux.arete.scoring.spi.SpecFormat;
import net.dublinux.arete.scoring.spi.SpecInput;
import net.dublinux.arete.scoring.spi.SpecScoringPlugin;
import net.dublinux.arete.scoring.spi.MatcherTestProvider;
import net.dublinux.arete.scoring.spi.MatcherTestRequest;
import net.dublinux.arete.scoring.spi.RuleDocumentation;
import net.dublinux.arete.scoring.spi.RuleDocumentationProvider;
import net.dublinux.arete.scoring.spi.ScoringResult;

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
public final class PolicyScoringPlugin implements SpecScoringPlugin, RuleDocumentationProvider, MatcherTestProvider {
    private static final String DOCUMENTATION_BASE_URL = "http://localhost:6809/plugins/generic-policy/rules/";
    private static final int MAX_YAML_CODE_POINTS = 50 * 1024 * 1024;
    private static final System.Logger LOG = System.getLogger(PolicyScoringPlugin.class.getName());

    static {
        // The plugin has its own classloader, so the host application's
        // swagger-parser YAML configuration does not reach this parser.
        DeserializationUtils.getOptions().setMaxYamlCodePoints(MAX_YAML_CODE_POINTS);
    }

    private volatile PolicyBundle bundle;
    private final PolicyBundleLoader bundleLoader = new PolicyBundleLoader();
    private final DistillMatcherEvaluator distillRuntime = new DistillMatcherEvaluator();

    @Override public String getId() { return "generic-policy"; }
    @Override public String getName() { return "Areté Policy Engine"; }
    @Override public String getVersion() { return "0.1.0-SNAPSHOT"; }

    @Override
    public Set<SpecFormat> getSupportedFormats() {
        return EnumSet.of(SpecFormat.OPENAPI3, SpecFormat.SWAGGER2);
    }

    @Override
    public List<String> getPolicies() {
        return activeBundle().policies().keySet().stream().toList();
    }

    @Override
    public Optional<String> getSuggestedScoreLevel(String policyName) {
        Policy policy = activeBundle().policies().get(policyName);
        if (policy == null) {
            return Optional.empty();
        }
        if (policy.passingScore() != null) {
            double bar = policy.passingScore();
            return Optional.of("score<" + (bar == Math.rint(bar) ? Long.toString((long) bar) : Double.toString(bar)));
        }
        return Optional.ofNullable(policy.scoreLevel());
    }

    @Override
    public java.util.OptionalDouble getPassingScore(String policyName) {
        Policy policy = activeBundle().policies().get(policyName);
        return policy == null || policy.passingScore() == null
                ? java.util.OptionalDouble.empty()
                : java.util.OptionalDouble.of(policy.passingScore());
    }

    @Override
    public synchronized void configure(Map<String, String> config) {
        bundle = bundleLoader.load(new ClasspathBundleResources(getClass().getClassLoader()),
                PolicyBundleLoader.LoadOptions.defaults(), loadUserPolicies(config));
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

    @Override
    public ScoringResult score(SpecInput input) {
        PolicyBundle currentBundle;
        try {
            currentBundle = activeBundle();
        } catch (BundleValidationException e) {
            return ScoringResult.pluginError("Could not load generic policy bundle: " + e.getMessage());
        }
        SwaggerParseResult parsed = new OpenAPIV3Parser().readContents(input.getContent(), null, new ParseOptions());
        if (parsed.getOpenAPI() == null) {
            String detail = parsed.getMessages() == null ? "unknown parse error" : String.join("; ", parsed.getMessages());
            return ScoringResult.parseError("OpenAPI parsing failed: " + detail);
        }

        Policy policy = currentBundle.policyOrDefault(input.getPolicy());
        Map<String, Object> api = OpenApiMapAdapter.toMap(parsed.getOpenAPI(), parsed.getMessages(), input.getContent());
        List<net.dublinux.arete.scoring.spi.Diagnostic> diagnostics = new ArrayList<>();
        double deductions = 0;
        boolean prohibitedMatched = false;
        int rulesEvaluated = 0;

        for (Map.Entry<String, PolicyDisposition> policyRule : policy.dispositions().entrySet()) {
            PolicyRule rule = currentBundle.rules().get(policyRule.getKey());
            rulesEvaluated++;
            List<net.dublinux.arete.scoring.policy.Diagnostic> matches;
            try {
                Matcher matcher = currentBundle.matchers().get(rule.matcherId());
                if (matcher == null) {
                    return ScoringResult.pluginError("Matcher '" + rule.matcherId() + "' required by " + rule.id() + " is not available in this bundle");
                }
                Map<String, Object> parameters = new LinkedHashMap<>(rule.parameters());
                parameters.putAll(policyRule.getValue().parameters());
                PolicyRule effectiveRule = new PolicyRule(rule.id(), rule.title(), rule.category(), rule.matcherId(), rule.scope(), parameters, rule.documentationMarkdown());
                matches = distillRuntime.execute(matcher, api, effectiveRule);
            } catch (MatcherEvaluationException e) {
                return ScoringResult.pluginError("Matcher '" + rule.matcherId() + "' failed for " + rule.id() + ": " + e.getMessage());
            }
            if (matches.isEmpty()) continue;

            PolicyDisposition disposition = policyRule.getValue();
            if (disposition instanceof Deduction deduction) deductions += deduction.points();
            else prohibitedMatched = true;

            for (net.dublinux.arete.scoring.policy.Diagnostic match : matches) {
                net.dublinux.arete.scoring.spi.Diagnostic.Builder diagnostic = net.dublinux.arete.scoring.spi.Diagnostic.builder()
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
        return ScoringResult.builder().status(ScoringResult.Status.SUCCESS).diagnostics(diagnostics)
                .rulesEvaluatedCount(rulesEvaluated).overallScore(effectiveScore)
                .overallScoreWithoutBlockers(qualityScore)
                .grade(policy.gradeFor(effectiveScore)).build();
    }

    @Override
    public ScoringResult testMatcher(MatcherTestRequest request) {
        try {
            SwaggerParseResult parsed = new OpenAPIV3Parser().readContents(request.spec(), null, new ParseOptions());
            if (parsed.getOpenAPI() == null) {
                String detail = parsed.getMessages() == null ? "unknown parse error" : String.join("; ", parsed.getMessages());
                return ScoringResult.parseError("OpenAPI parsing failed: " + detail);
            }
            if (!"distill".equals(request.language())) {
                throw new MatcherEvaluationException("Unsupported matcher language: " + request.language());
            }
            Matcher matcher = new Matcher(request.matcherId(), request.language(), request.source(),
                    List.of(request.scope()), Map.of());
            distillRuntime.validate(matcher);
            Map<String, Object> api = OpenApiMapAdapter.toMap(parsed.getOpenAPI(), parsed.getMessages(), request.spec());
            PolicyRule rule = new PolicyRule(request.matcherId(), request.matcherId(), "Matcher test",
                    request.matcherId(), request.scope(), request.parameters(), "");
            List<Diagnostic> matches = distillRuntime.execute(matcher, api, rule);
            List<net.dublinux.arete.scoring.spi.Diagnostic> diagnostics = matches.stream().map(match -> {
                net.dublinux.arete.scoring.spi.Diagnostic.Builder diagnostic = net.dublinux.arete.scoring.spi.Diagnostic.builder()
                        .ruleId(request.matcherId()).title(request.matcherId()).description(match.message())
                        .severity(Severity.WARNING);
                if (match.pointer() != null) diagnostic.pointer(match.pointer());
                if (match.path() != null) diagnostic.paths(List.of(match.path()));
                return diagnostic.build();
            }).toList();
            return ScoringResult.success(diagnostics, 1);
        } catch (MatcherEvaluationException | BundleValidationException e) {
            return ScoringResult.pluginError(e.getMessage());
        } catch (RuntimeException e) {
            return ScoringResult.pluginError("Matcher test failed: " + e.getMessage());
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
