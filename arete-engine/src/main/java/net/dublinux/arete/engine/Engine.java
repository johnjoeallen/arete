package net.dublinux.arete.engine;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import io.swagger.v3.parser.util.DeserializationUtils;
import net.dublinux.arete.engine.api.RuleOutcome;
import net.dublinux.arete.engine.api.Severity;
import net.dublinux.arete.engine.api.SpecInput;
import net.dublinux.arete.engine.api.MatcherTestRequest;
import net.dublinux.arete.engine.api.RuleDocumentation;
import net.dublinux.arete.engine.api.ScoringResult;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Set;

/**
 * The Areté engine: loads a policy bundle, scores a spec against one of its policies, and answers the
 * questions a front end asks about the bundle (its policies, rule documentation, a matcher test run).
 *
 * <p>Create one, call {@link #configure(Map)} once, then share it: scoring is thread-safe once configured.
 * It is not final so a test can stand in for it.
 */
public class Engine {
    /** Identifier the application uses for this engine. */
    public static final String ID = "generic-policy";

    /** Where a rule's documentation page is, as a base URL ending in "/"; null when nothing serves one (a build, the CLI). */
    private volatile String documentationBaseUrl;
    private static final int MAX_YAML_CODE_POINTS = 50 * 1024 * 1024;
    private static final System.Logger LOG = System.getLogger(Engine.class.getName());

    static {
        // Parse large specs: raise swagger-parser's YAML size limit for every Engine in this JVM.
        DeserializationUtils.getOptions().setMaxYamlCodePoints(MAX_YAML_CODE_POINTS);
    }

    private volatile PolicyBundle bundle;
    private final PolicyBundleLoader bundleLoader = new PolicyBundleLoader();
    private final DistillMatcherEvaluator distillRuntime = new DistillMatcherEvaluator();

    public String getId() { return ID; }
    public String getName() { return "Areté Policy Engine"; }
    public String getVersion() { return "0.1.0-SNAPSHOT"; }

    public List<String> getPolicies() {
        return activeBundle().policies().keySet().stream().toList();
    }

    /**
     * This engine reports only {@code PROHIBITED} matches at {@code ERROR}; deductions are
     * {@code WARNING}. Labelling ERROR "Blocker" makes the panel's severity filter show exactly
     * the rules that zero the score.
     */
    public String getSeverityLabel(Severity severity) {
        return switch (severity) {
            case ERROR -> "Blocker";
            case WARNING -> "Warning";
            case INFO -> "Info";
            case HINT -> "Hint";
        };
    }

    /** What the loaded bundle holds, for a tool that wants to say so. */
    public record BundleInfo(String bundleId, String bundleVersion, List<String> policies, int rules, int matchers) { }

    public BundleInfo bundleInfo() {
        PolicyBundle current = activeBundle();
        return new BundleInfo(current.bundleId(), current.bundleVersion(), List.copyOf(current.policies().keySet()),
                current.rules().size(), current.matchers().size());
    }

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

    public java.util.OptionalDouble getPassingScore(String policyName) {
        Policy policy = activeBundle().policies().get(policyName);
        return policy == null || policy.passingScore() == null
                ? java.util.OptionalDouble.empty()
                : java.util.OptionalDouble.of(policy.passingScore());
    }

    /**
     * Loads the bundles this engine scores with. Keys, all optional, each also readable from the system
     * property named in brackets:
     * <ul>
     *   <li>{@code policy-sources} ({@code arete.policy.sources}) — comma-separated {@link PolicySource}s,
     *       layered in order; default {@code classpath:api-policy};</li>
     *   <li>{@code maven-repositories} ({@code arete.policy.maven-repositories}) — comma-separated base URLs
     *       for {@code maven:} sources;</li>
     *   <li>{@code require-pin} ({@code arete.policy.require-pin}) — {@code true} to refuse a remote source
     *       without a {@code sha256};</li>
     *   <li>{@code maven-settings} ({@code arete.policy.maven-settings}) — a settings.xml path, or {@code default} for the
     *       usual locations, read for repositories, mirrors, credentials and proxies; {@code maven-profiles} activates profiles;</li>
     *   <li>{@code cache-dir} ({@code arete.policy.cache-dir}) — where fetched bundles are kept;</li>
     *   <li>{@code policies-dir} ({@code arete.policy.policies-dir}) — extra {@code *.md} policies, default
     *       {@code ~/.arete/policies};</li>
     *   <li>{@code documentation-base-url} ({@code arete.policy.documentation-base-url}) — where rule documentation is
     *       served, so each finding can link to it; unset, findings carry no link.</li>
     * </ul>
     * To embed the engine with explicit settings, use {@link #builder()} instead.
     */
    public synchronized void configure(Map<String, String> config) {
        String documentation = configOrProperty(config, "documentation-base-url", "arete.policy.documentation-base-url");
        if (documentation != null && !documentation.isBlank()) setDocumentationBaseUrl(documentation.strip());
        List<PolicySource> sources = new ArrayList<>();
        String configured = configOrProperty(config, "policy-sources", "arete.policy.sources");
        if (configured != null && !configured.isBlank()) {
            for (String spec : configured.split(",")) if (!spec.isBlank()) sources.add(PolicySource.parse(spec));
        }
        List<String> repositories = new ArrayList<>();
        String repos = configOrProperty(config, "maven-repositories", "arete.policy.maven-repositories");
        if (repos != null && !repos.isBlank()) for (String repo : repos.split(",")) if (!repo.isBlank()) repositories.add(repo.strip());
        String cache = configOrProperty(config, "cache-dir", "arete.policy.cache-dir");
        load(new Settings(sources, repositories,
                "true".equalsIgnoreCase(configOrProperty(config, "require-pin", "arete.policy.require-pin")),
                cache != null && !cache.isBlank() ? Path.of(cache.trim()) : defaultCacheDir(),
                Map.of(), null, loadUserPolicies(config), mavenSettingsFrom(config), profilesFrom(config)));
    }

    /** {@code maven-settings}: a path to a settings.xml, or {@code default} for the usual locations; unset reads none. */
    private static MavenSettings mavenSettingsFrom(Map<String, String> config) {
        String configured = configOrProperty(config, "maven-settings", "arete.policy.maven-settings");
        if (configured == null || configured.isBlank()) return null;
        return "default".equals(configured.strip()) ? MavenSettings.load() : MavenSettings.load(Path.of(configured.strip()));
    }

    private static Set<String> profilesFrom(Map<String, String> config) {
        String configured = configOrProperty(config, "maven-profiles", "arete.policy.maven-profiles");
        Set<String> profiles = new java.util.LinkedHashSet<>();
        if (configured != null) for (String id : configured.split(",")) if (!id.isBlank()) profiles.add(id.strip());
        return profiles;
    }

    private static Path defaultCacheDir() {
        return Path.of(System.getProperty("user.home", ""), ".arete", "cache", "policies");
    }

    /** What an engine is built from: where its bundles come from and how they are checked. */
    private record Settings(List<PolicySource> sources, List<String> mavenRepositories, boolean requirePin, Path cacheDir,
            Map<String, String> headers, java.net.http.HttpClient http, List<PolicyBundleLoader.OverlayPolicy> overlays,
            MavenSettings mavenSettings, Set<String> mavenProfiles) { }

    private void load(Settings settings) {
        List<PolicySource> sources = settings.sources().isEmpty()
                ? List.of(PolicySource.parse("classpath:api-policy")) : settings.sources();
        List<MavenSettings.Repository> repositories = new ArrayList<>();
        java.net.http.HttpClient http = settings.http();
        MavenSettings maven = settings.mavenSettings();
        if (maven != null) {
            // Maven looks in its local repository first, then the repositories the settings name.
            repositories.add(new MavenSettings.Repository(maven.localRepository().toUri().toString(), Map.of()));
        }
        for (String url : settings.mavenRepositories()) repositories.add(new MavenSettings.Repository(url, Map.of()));
        if (maven != null) {
            repositories.addAll(maven.repositories(settings.mavenProfiles()));
            if (http == null && maven.proxySelector() != null) {
                java.net.http.HttpClient.Builder client = java.net.http.HttpClient.newBuilder()
                        .connectTimeout(java.time.Duration.ofSeconds(10))
                        .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                        .proxy(maven.proxySelector());
                if (maven.proxyAuthenticator() != null) client.authenticator(maven.proxyAuthenticator());
                http = client.build();
            }
        }
        PolicySourceResolver resolver = new PolicySourceResolver(http, settings.cacheDir(), repositories,
                settings.headers(), settings.requirePin(), getClass().getClassLoader());
        PolicyBundle loaded = null;
        for (PolicySource source : sources) {
            PolicyBundle next = bundleLoader.load(resolver.resolve(source), loaded, List.of());
            if (source.version() != null && !source.version().equals(next.bundleVersion())) {
                throw new BundleValidationException("policy source " + source.uri() + " is bundleVersion " + next.bundleVersion()
                        + " but is pinned to " + source.version());
            }
            loaded = next;
        }
        bundle = bundleLoader.withOverlays(loaded, settings.overlays());
    }

    /** Starts building an engine with explicit policy sources; nothing is read from the user's home directory. */
    public static Builder builder() { return new Builder(); }

    /**
     * Where rule documentation is served, a base URL the rule id is appended to. Findings link to it; with none (the
     * default) they carry no link, which is right for a build or a CLI run that has no documentation site.
     */
    public void setDocumentationBaseUrl(String baseUrl) {
        this.documentationBaseUrl = baseUrl == null || baseUrl.isBlank() ? null : baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    /** Collects an engine's policy sources and how they are fetched and checked, then loads them in {@link #build()}. */
    public static final class Builder {
        private final List<PolicySource> sources = new ArrayList<>();
        private final List<String> repositories = new ArrayList<>();
        private final Map<String, String> headers = new LinkedHashMap<>();
        private boolean requirePin;
        private Path cacheDir = defaultCacheDir();
        private Path userPolicies;
        private java.net.http.HttpClient http;
        private MavenSettings mavenSettings;
        private final java.util.Set<String> mavenProfiles = new java.util.LinkedHashSet<>();
        private String documentationBaseUrl;

        /** Adds a source. Sources layer in the order added; with none, the bundle in the jar is used. */
        public Builder policySource(PolicySource source) { sources.add(source); return this; }

        public Builder policySource(String spec) { return policySource(PolicySource.parse(spec)); }

        /** A Maven-layout repository (an https or {@code file:} base URL) for {@code maven:} sources. */
        public Builder mavenRepository(String baseUrl) { repositories.add(baseUrl); return this; }

        /** Refuse a remote source that has no {@code sha256} pin. */
        public Builder requirePin(boolean require) { this.requirePin = require; return this; }

        /** Where fetched bundles are kept, by digest; {@code null} turns the cache off. */
        public Builder cacheDir(Path dir) { this.cacheDir = dir; return this; }

        /** A directory of extra {@code *.md} policies, added after the sources. */
        public Builder userPoliciesDir(Path dir) { this.userPolicies = dir; return this; }

        /** A header sent with every fetch, for a repository that wants a token. */
        public Builder requestHeader(String name, String value) { headers.put(name, value); return this; }

        public Builder httpClient(java.net.http.HttpClient client) { this.http = client; return this; }

        /**
         * Reads the usual Maven settings ({@code ~/.m2/settings.xml} over {@code $MAVEN_HOME/conf/settings.xml}) for
         * where {@code maven:} sources are fetched from: the local repository, mirrors, server credentials, proxies and
         * the repositories of active profiles. After the local repository, repositories given with {@link #mavenRepository} are tried before the settings'.
         */
        public Builder mavenSettings() { this.mavenSettings = MavenSettings.load(); return this; }

        /** As {@link #mavenSettings()} but from one file, as {@code mvn -s file} does. The file must exist. */
        public Builder mavenSettings(Path settingsXml) { this.mavenSettings = MavenSettings.load(settingsXml); return this; }

        /**
         * As {@link #mavenSettings()} but from the given files, a later one over an earlier (global then user); a file that
         * does not exist is skipped. This is how a Maven plugin passes the settings files its own build was started with.
         */
        public Builder mavenSettings(List<Path> settingsFiles) { this.mavenSettings = MavenSettings.load(settingsFiles); return this; }

        /** Activates a settings.xml profile, as {@code mvn -P id} does. */
        public Builder mavenProfile(String id) { mavenProfiles.add(id); return this; }

        /** Where rule documentation is served; see {@link Engine#setDocumentationBaseUrl}. */
        public Builder documentationBaseUrl(String baseUrl) { this.documentationBaseUrl = baseUrl; return this; }

        public Engine build() {
            Engine engine = new Engine();
            List<PolicyBundleLoader.OverlayPolicy> overlays = userPolicies == null ? List.of() : readPolicies(userPolicies);
            engine.load(new Settings(List.copyOf(sources), List.copyOf(repositories), requirePin, cacheDir, Map.copyOf(headers), http, overlays,
                    mavenSettings, java.util.Set.copyOf(mavenProfiles)));
            engine.setDocumentationBaseUrl(documentationBaseUrl);
            return engine;
        }
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
        return readPolicies(dir);
    }

    private static List<PolicyBundleLoader.OverlayPolicy> readPolicies(Path dir) {
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

    /**
     * Resolve {@code $ref}s so referenced request bodies, responses and headers carry their content.
     * Not {@code resolveFully}: schema {@code $ref}s must stay visible to the inline-schema checks.
     */
    private static ParseOptions parseOptions() {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        return options;
    }

    public ScoringResult score(SpecInput input) {
        return score(input, Overrides.none());
    }

    /**
     * Scores {@code input} under its policy with a team's {@link Overrides} applied. Overrides that name an
     * unknown rule, a locked rule, or parameters the rule's matcher does not take make the run fail with an
     * error status rather than being ignored.
     */
    public ScoringResult score(SpecInput input, Overrides overrides) {
        PolicyBundle currentBundle;
        try {
            currentBundle = activeBundle();
        } catch (BundleValidationException e) {
            return ScoringResult.engineError("Could not load generic policy bundle: " + e.getMessage());
        }
        SwaggerParseResult parsed = new OpenAPIV3Parser().readContents(input.getContent(), null, parseOptions());
        if (parsed.getOpenAPI() == null) {
            String detail = parsed.getMessages() == null ? "unknown parse error" : String.join("; ", parsed.getMessages());
            return ScoringResult.parseError("OpenAPI parsing failed: " + detail);
        }

        Policy policy;
        try {
            policy = applyOverrides(currentBundle.policyOrDefault(input.getPolicy()), currentBundle, overrides);
        } catch (BundleValidationException e) {
            return ScoringResult.engineError("Could not apply the overrides: " + e.getMessage());
        }
        Map<String, Object> api = OpenApiMapAdapter.toMap(parsed.getOpenAPI(), parsed.getMessages(), input.getContent());
        List<net.dublinux.arete.engine.api.Diagnostic> diagnostics = new ArrayList<>();
        double deductions = 0;
        boolean prohibitedMatched = false;
        int rulesEvaluated = 0;
        List<RuleOutcome> outcomes = new ArrayList<>();

        for (Map.Entry<String, PolicyDisposition> policyRule : policy.dispositions().entrySet()) {
            PolicyRule rule = currentBundle.rules().get(policyRule.getKey());
            rulesEvaluated++;
            List<net.dublinux.arete.engine.Diagnostic> matches;
            try {
                Matcher matcher = currentBundle.matchers().get(rule.matcherId());
                if (matcher == null) {
                    return ScoringResult.engineError("Matcher '" + rule.matcherId() + "' required by " + rule.id() + " is not available in this bundle");
                }
                Map<String, Object> parameters = new LinkedHashMap<>(rule.parameters());
                parameters.putAll(policyRule.getValue().parameters());
                if (policyRule.getValue() instanceof Graduated byValue && byValue.byValue() && !byValue.tiers().isEmpty()) {
                    String problem = deriveThreshold(matcher, byValue, parameters);
                    if (problem != null) return ScoringResult.engineError("Rule " + rule.id() + ": " + problem);
                }
                PolicyRule effectiveRule = new PolicyRule(rule.id(), rule.title(), rule.category(), rule.matcherId(), rule.scope(), parameters, rule.documentationMarkdown());
                matches = distillRuntime.execute(matcher, api, effectiveRule);
            } catch (MatcherEvaluationException e) {
                return ScoringResult.engineError("Matcher '" + rule.matcherId() + "' failed for " + rule.id() + ": " + e.getMessage());
            }
            PolicyDisposition disposition = policyRule.getValue();
            int count = matches.size();
            double measure = count;
            boolean violated;
            List<net.dublinux.arete.engine.Diagnostic> reported;
            if (disposition.expectMatch()) {
                // The matcher looks for something that should be there; finding nothing is the violation.
                violated = count == 0;
                count = violated ? 1 : 0;
                measure = count;
                reported = violated
                        ? List.of(new net.dublinux.arete.engine.Diagnostic("/", "API", "Expected at least one match but found none: " + rule.title()))
                        : List.of();
            } else {
                if (disposition instanceof Graduated byValue && byValue.byValue()) {
                    // Charged by what the matcher measured: the worst value among its occurrences.
                    measure = 0;
                    for (net.dublinux.arete.engine.Diagnostic match : matches) {
                        if (match.value() == null) {
                            return ScoringResult.engineError("Rule " + rule.id() + " is charged by value (measure: value) but its matcher '"
                                    + rule.matcherId() + "' reports occurrences without one");
                        }
                        measure = Math.max(measure, match.value());
                    }
                }
                violated = disposition instanceof Graduated graduated ? graduated.violatedAt(measure) : count > 0;
                reported = violated ? matches : List.of();
            }
            if (!violated) continue;

            double cost = 0;
            if (disposition instanceof Deduction deduction) cost = deduction.points();
            else if (disposition instanceof Graduated graduated) cost = graduated.costAt(measure);
            else prohibitedMatched = true;
            deductions += cost;
            outcomes.add(new RuleOutcome(rule.id(), count, measure, cost, disposition instanceof Graduated, disposition instanceof Prohibited));

            for (net.dublinux.arete.engine.Diagnostic match : reported) {
                net.dublinux.arete.engine.api.Diagnostic.Builder diagnostic = net.dublinux.arete.engine.api.Diagnostic.builder()
                        .ruleId(rule.id()).title(rule.title()).description(match.message())
                        .severity(disposition instanceof Prohibited ? Severity.ERROR : Severity.WARNING)
                        .scoreImprovement(cost);
                if (documentationBaseUrl != null) diagnostic.documentationUrl(documentationBaseUrl + rule.id());
                if (match.pointer() != null) diagnostic.pointer(match.pointer());
                if (match.path() != null) diagnostic.paths(List.of(match.path()));
                if (match.value() != null) diagnostic.value(match.value());
                diagnostics.add(diagnostic.build());
            }
        }

        double qualityScore = Math.max(0, 100 - deductions);
        double effectiveScore = prohibitedMatched ? 0 : qualityScore;
        return ScoringResult.builder().status(ScoringResult.Status.SUCCESS).diagnostics(diagnostics)
                .rulesEvaluatedCount(rulesEvaluated).overallScore(effectiveScore)
                .overallScoreWithoutBlockers(qualityScore).ruleOutcomes(outcomes)
                .grade(policy.gradeFor(effectiveScore)).build();
    }

    /** The policy with a team's overrides applied; the bundle's own policy objects are never changed. */
    private static Policy applyOverrides(Policy policy, PolicyBundle bundle, Overrides overrides) {
        if (overrides == null || overrides.rules().isEmpty()) return policy;
        Map<String, PolicyDisposition> dispositions = new LinkedHashMap<>(policy.dispositions());
        for (Overrides.RuleOverride override : overrides.rules().values()) {
            String id = override.ruleId();
            PolicyRule rule = bundle.rules().get(id);
            if (rule == null) throw new BundleValidationException("overrides." + id + ": no such rule");
            if (policy.locked().contains(id)) {
                throw new BundleValidationException("overrides." + id + ": the policy '" + policy.id() + "' locks this rule, so it cannot be overridden");
            }
            PolicyDisposition current = dispositions.get(id);
            if (current == null) continue; // this policy does not run the rule; the override has nothing to change
            if (override.disabled()) {
                dispositions.remove(id);
                continue;
            }
            Map<String, Object> parameters = new LinkedHashMap<>(current.parameters());
            if (override.parameters() != null) {
                Matcher matcher = bundle.matchers().get(rule.matcherId());
                Map<String, Object> changes = override.parameters();
                if (matcher != null) {
                    PolicyBundleLoader.validateParameterOverrides(".arete.yaml", id, changes, matcher);
                    changes = PolicyBundleLoader.coerceListValues(changes, matcher);
                }
                parameters.putAll(changes);
            }
            if (override.points() != null) dispositions.put(id, new Deduction(override.points(), parameters, current.expectMatch()));
            else dispositions.put(id, current.withParameters(parameters));
        }
        return new Policy(policy.id(), dispositions, policy.scoreLevel(), policy.passingScore(), policy.grades(), policy.locked());
    }

    public ScoringResult testMatcher(MatcherTestRequest request) {
        try {
            SwaggerParseResult parsed = new OpenAPIV3Parser().readContents(request.spec(), null, parseOptions());
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
            List<net.dublinux.arete.engine.api.Diagnostic> diagnostics = matches.stream().map(match -> {
                net.dublinux.arete.engine.api.Diagnostic.Builder diagnostic = net.dublinux.arete.engine.api.Diagnostic.builder()
                        .ruleId(request.matcherId()).title(request.matcherId()).description(match.message())
                        .severity(Severity.WARNING);
                if (match.pointer() != null) diagnostic.pointer(match.pointer());
                if (match.path() != null) diagnostic.paths(List.of(match.path()));
                return diagnostic.build();
            }).toList();
            return ScoringResult.success(diagnostics, 1);
        } catch (MatcherEvaluationException | BundleValidationException e) {
            return ScoringResult.engineError(e.getMessage());
        } catch (RuntimeException e) {
            return ScoringResult.engineError("Matcher test failed: " + e.getMessage());
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

    public Optional<RuleDocumentation> getRuleDocumentation(String matcherId) {
        PolicyRule rule = activeBundle().rules().get(matcherId);
        return rule == null ? Optional.empty() : Optional.of(new RuleDocumentation(rule.title(), interpolateDocumentation(rule.documentationMarkdown(), rule.parameters())));
    }

    /**
     * A rule charged by value reports only the occurrences past its matcher's threshold, so a threshold above the lowest
     * tier would hide that tier. When the policy does not set the threshold itself, it is derived from the lowest tier:
     * {@code maximum} (reports above it) is one below the tier, {@code minimum} (reports from it) is the tier. A threshold the policy sets that would hide the lowest tier is a
     * scoring error, returned as the message.
     */
    private static String deriveThreshold(Matcher matcher, Graduated byValue, Map<String, Object> parameters) {
        int lowest = byValue.tiers().get(0).minimum();
        Object maximum = byValue.parameters().get("maximum");
        Object minimum = byValue.parameters().get("minimum");
        if (matcher.parameters().containsKey("maximum")) {
            if (maximum == null) parameters.put("maximum", lowest - 1);
            else if (maximum instanceof Number n && n.doubleValue() >= lowest) {
                return "maximum " + maximum + " hides the lowest tier (" + lowest + "): it must be " + (lowest - 1) + " or less";
            }
        } else if (matcher.parameters().containsKey("minimum")) {
            if (minimum == null) parameters.put("minimum", lowest);
            else if (minimum instanceof Number n && n.doubleValue() > lowest) {
                return "minimum " + minimum + " hides the lowest tier (" + lowest + "): it must be " + lowest + " or less";
            }
        }
        return null;
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
