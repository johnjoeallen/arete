package com.speculate.validation.policy;

import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import net.dublinux.speculate.validation.spi.Severity;
import net.dublinux.speculate.validation.spi.SpecFormat;
import net.dublinux.speculate.validation.spi.SpecInput;
import net.dublinux.speculate.validation.spi.SpecValidationPlugin;
import net.dublinux.speculate.validation.spi.ValidationResult;
import net.dublinux.speculate.validation.spi.Violation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** First working implementation of the bundled generic policy engine. */
public final class GenericPolicyValidationPlugin implements SpecValidationPlugin {
    private volatile PolicyBundle bundle;
    private final PolicyBundleLoader bundleLoader = new PolicyBundleLoader();
    private final GroovyDetectorRuntime detectorRuntime = new GroovyDetectorRuntime();

    @Override public String getId() { return "generic-policy"; }
    @Override public String getName() { return "Generic API Policy"; }
    @Override public String getVersion() { return "0.1.0-SNAPSHOT"; }

    @Override
    public Set<SpecFormat> getSupportedFormats() {
        return EnumSet.of(SpecFormat.OPENAPI3, SpecFormat.SWAGGER2);
    }

    @Override
    public List<String> getRuleSets() {
        return activeBundle().policies().keySet().stream().toList();
    }

    @Override
    public synchronized void configure(Map<String, String> config) {
        bundle = bundleLoader.load(new ClasspathBundleResources(getClass().getClassLoader()));
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
        int deductions = 0;
        boolean prohibitedMatched = false;
        int rulesEvaluated = 0;

        for (Map.Entry<String, PolicyDisposition> policyRule : policy.dispositions().entrySet()) {
            Rule rule = currentBundle.rules().get(policyRule.getKey());
            rulesEvaluated++;
            List<Occurrence> occurrences;
            try {
                occurrences = detectorRuntime.execute(currentBundle.detectors().get(rule.detector()), api, rule);
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
                        .scoreImprovement(disposition instanceof Deduction deduction ? deduction.points() : 0);
                if (occurrence.pointer() != null) violation.pointer(occurrence.pointer());
                violations.add(violation.build());
            }
        }

        int qualityScore = Math.max(0, 100 - deductions);
        int effectiveScore = prohibitedMatched ? 0 : qualityScore;
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
}
