package speculate.validation.zally;

import com.fasterxml.jackson.core.JsonPointer;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.zalando.zally.core.ApiValidator;
import org.zalando.zally.core.CompositeRulesValidator;
import org.zalando.zally.core.ContextRulesValidator;
import org.zalando.zally.core.DefaultContextFactory;
import org.zalando.zally.core.JsonRulesValidator;
import org.zalando.zally.core.Result;
import org.zalando.zally.core.RulesManager;
import org.zalando.zally.core.RulesPolicy;
import speculate.validation.spi.SpecFormat;
import speculate.validation.spi.SpecInput;
import speculate.validation.spi.SpecValidationPlugin;
import speculate.validation.spi.Violation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wraps Zalando's <a href="https://github.com/zalando/zally">Zally</a> linter
 * ({@code zally-core} + the {@code zally-ruleset-zalando} ruleset) as a
 * {@link SpecValidationPlugin}. Speculate's bundled default validator — this
 * is the "core" ruleset shipped in {@code plugins/}; a separate,
 * organization-specific ruleset (e.g. a Mastercard variant) is expected to
 * be supplied and dropped into {@code ~/.speculate/plugins} independently,
 * as its own plugin jar with its own {@code getId()}.
 *
 * <p>{@link RulesManager.Companion#fromClassLoader} discovers rules via a
 * {@code ServiceLoader}-style scan of {@code this::class.java.classLoader}
 * (verified by disassembling zally-core 2.1.1) for
 * {@code META-INF/services/org.zalando.zally.rule.api.Rule} — i.e. it scans
 * whatever jar this class itself was loaded from. Since Speculate loads each
 * plugin through its own isolated, single-jar {@code URLClassLoader}, that
 * only works because this module is shaded into one self-contained jar
 * bundling {@code zally-ruleset-zalando} (see pom.xml) — an unshaded build
 * would silently discover zero rules.
 */
public final class ZallyValidationPlugin implements SpecValidationPlugin {

    private final ApiValidator validator;
    private final RulesManager rulesManager;
    private List<String> ignoredRuleIds = Collections.emptyList();

    public ZallyValidationPlugin() {
        // Zally's own RulesConfigKt.getRulesConfig() helper calls
        // ConfigFactory.load("rules-config.conf") with no explicit
        // ClassLoader, which resolves resources (including the per-rule
        // reference.conf entries it falls back to, e.g. ApiAudienceRule's
        // default audience list) via whatever classloader typesafe-config
        // picks implicitly. Speculate loads this plugin through its own
        // isolated, single-jar URLClassLoader, and that implicit resolution
        // doesn't reliably land on it — the symptom is
        // ConfigException.Missing for a key that genuinely is present in
        // this jar's bundled reference.conf. Calling the explicit-ClassLoader
        // overload instead removes the ambiguity.
        ClassLoader loader = ZallyValidationPlugin.class.getClassLoader();
        Config config = ConfigFactory.load(loader, "rules-config.conf");
        this.rulesManager = RulesManager.Companion.fromClassLoader(config);
        this.validator = new CompositeRulesValidator(
                new ContextRulesValidator(rulesManager, new DefaultContextFactory()),
                new JsonRulesValidator(rulesManager));
    }

    @Override
    public String getId() {
        return "zally-core";
    }

    @Override
    public String getName() {
        return "Zally (Zalando API Guidelines Linter)";
    }

    @Override
    public String getVersion() {
        return "zally-core 2.1.1";
    }

    @Override
    public Set<SpecFormat> getSupportedFormats() {
        return EnumSet.of(SpecFormat.OPENAPI3, SpecFormat.SWAGGER2);
    }

    /**
     * Recognizes a single key, {@code "ignoreRules"}: a comma-separated list
     * of Zally rule IDs to skip, e.g. {@code "150,175"}. Absent or blank
     * means every discovered rule runs.
     */
    @Override
    public void configure(Map<String, String> config) {
        String ignore = config.get("ignoreRules");
        if (ignore == null || ignore.isBlank()) {
            this.ignoredRuleIds = Collections.emptyList();
            return;
        }
        List<String> ids = new ArrayList<>();
        for (String id : ignore.split(",")) {
            String trimmed = id.trim();
            if (!trimmed.isEmpty()) {
                ids.add(trimmed);
            }
        }
        this.ignoredRuleIds = ids;
    }

    @Override
    public speculate.validation.spi.ValidationResult validate(SpecInput input) {
        // Same context-classloader swap as the constructor, and for the same
        // reason: a defensive belt-and-suspenders in case some individual
        // rule's @Check resolves config lazily at validate-time rather than
        // when RulesManager.fromClassLoader instantiated it.
        ClassLoader previousContextLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(ZallyValidationPlugin.class.getClassLoader());
        try {
            RulesPolicy policy = new RulesPolicy(ignoredRuleIds);
            List<Result> results = validator.validate(input.getContent(), policy, "");
            List<Violation> violations = new ArrayList<>(results.size());
            for (Result result : results) {
                violations.add(toViolation(result));
            }
            int rulesEvaluatedCount = rulesManager.checks(policy).size();
            return speculate.validation.spi.ValidationResult.success(violations, rulesEvaluatedCount);
        } catch (Exception e) {
            return speculate.validation.spi.ValidationResult.pluginError(
                    "Zally failed to validate the spec: " + e);
        } finally {
            Thread.currentThread().setContextClassLoader(previousContextLoader);
        }
    }

    private static Violation toViolation(Result result) {
        Violation.Builder builder = Violation.builder()
                .ruleId(result.getId())
                .title(result.getTitle())
                .description(result.getDescription())
                .severity(mapSeverity(result.getViolationType()));

        JsonPointer pointer = result.getPointer();
        if (pointer != null) {
            builder.pointer(pointer.toString());
        }
        if (result.getUrl() != null) {
            builder.documentationUrl(result.getUrl().toString());
        }
        // Result.getLines() is a kotlin.ranges.IntRange; deliberately not
        // pulling a direct kotlin-stdlib compile dependency into this module
        // just to unwrap it — lineNumber is optional in the SPI, and pointer
        // (a structural JSON Pointer) is the more reliable location anyway.
        return builder.build();
    }

    private static speculate.validation.spi.Severity mapSeverity(org.zalando.zally.rule.api.Severity severity) {
        switch (severity) {
            case MUST:
                return speculate.validation.spi.Severity.ERROR;
            case SHOULD:
                return speculate.validation.spi.Severity.WARNING;
            case MAY:
                return speculate.validation.spi.Severity.INFO;
            case HINT:
                return speculate.validation.spi.Severity.HINT;
            default:
                throw new IllegalArgumentException("Unknown Zally severity: " + severity);
        }
    }
}
