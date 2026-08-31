package net.dublinux.arete.plugin;

import net.dublinux.arete.validation.spi.Severity;

/**
 * A pass level for automated gating, in the fixed grammar
 * {@code never | error | blocker | score<NN}. {@code policy} (use each policy's
 * own suggested level) is resolved by the caller before it reaches this type.
 *
 * @param kind the criterion
 * @param minScore only meaningful for {@link Kind#MIN_SCORE}
 */
public record ScoreLevel(Kind kind, double minScore) {

    public enum Kind { NEVER, ERROR, BLOCKER, MIN_SCORE }

    public static final ScoreLevel NEVER = new ScoreLevel(Kind.NEVER, Double.NaN);
    public static final ScoreLevel BLOCKER = new ScoreLevel(Kind.BLOCKER, Double.NaN);
    public static final ScoreLevel ERROR = new ScoreLevel(Kind.ERROR, Double.NaN);

    /** Parses a level string; throws {@link IllegalArgumentException} on anything else. */
    public static ScoreLevel parse(String raw) {
        String s = raw == null ? "" : raw.trim().toLowerCase();
        return switch (s) {
            case "", "never" -> NEVER;
            case "error" -> ERROR;
            case "blocker" -> BLOCKER;
            default -> {
                if (s.startsWith("score<")) {
                    try {
                        yield new ScoreLevel(Kind.MIN_SCORE, Double.parseDouble(s.substring(6).trim()));
                    } catch (NumberFormatException ignored) {
                        // fall through
                    }
                }
                throw new IllegalArgumentException(
                        "score level must be never, error, blocker, or score<NN (got '" + raw + "')");
            }
        };
    }

    public String describe() {
        return switch (kind) {
            case NEVER -> "never";
            case ERROR -> "error";
            case BLOCKER -> "blocker";
            case MIN_SCORE -> "score<" + (minScore == Math.rint(minScore)
                    ? Long.toString((long) minScore) : Double.toString(minScore));
        };
    }

    /** True when this level is not met by the given result — i.e. the combination FAILS. */
    public boolean failedBy(AggregatedValidationResult result) {
        return switch (kind) {
            case NEVER -> false;
            case ERROR -> countAtLeastOne(result, Severity.ERROR);
            case BLOCKER -> blockerFired(result);
            case MIN_SCORE -> !Double.isNaN(result.overallScore()) && result.overallScore() < minScore;
        };
    }

    private static boolean countAtLeastOne(AggregatedValidationResult result, Severity severity) {
        return result.diagnostics().stream().anyMatch(d -> d.diagnostic().getSeverity() == severity);
    }

    /**
     * A blocker fired when a computed score sits below the score-without-blockers
     * (something reduced it to zero); if scores weren't computed, fall back to
     * "any error-severity finding".
     */
    private static boolean blockerFired(AggregatedValidationResult result) {
        double score = result.overallScore();
        double withoutBlockers = result.overallScoreWithoutBlockers();
        if (!Double.isNaN(score) && !Double.isNaN(withoutBlockers)) {
            return withoutBlockers - score > 0.01;
        }
        return countAtLeastOne(result, Severity.ERROR);
    }
}
