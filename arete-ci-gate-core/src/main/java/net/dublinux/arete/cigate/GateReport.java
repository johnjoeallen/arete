package net.dublinux.arete.cigate;

import java.util.List;
import java.util.Locale;

/**
 * The plain-text report, identical whether it came from the Maven or the
 * Gradle plugin. One row per combination; optional rows are always shown and
 * labelled non-gating so nothing is hidden.
 */
public final class GateReport {

    private GateReport() {
    }

    public static String render(GateRequest request, GateOutcome outcome) {
        StringBuilder sb = new StringBuilder();
        sb.append("Areté CI Gate — ").append(request.specDisplayName())
                .append("   (arete: ").append(request.areteBaseUrl()).append(")\n\n");

        List<CombinationOutcome> rows = outcome.combinations();
        int nameWidth = Math.max("COMBINATION".length(),
                rows.stream().mapToInt(r -> r.run().length()).max().orElse(0));

        sb.append(pad("COMBINATION", nameWidth)).append("  SCORE  GRADE  LEVEL           RESULT  GATING\n");
        for (CombinationOutcome r : rows) {
            sb.append(pad(r.run(), nameWidth)).append("  ")
                    .append(pad(r.score() == null ? "-" : String.format(Locale.ROOT, "%.1f", r.score()), 5)).append("  ")
                    .append(pad(r.grade() == null ? "-" : r.grade(), 5)).append("  ")
                    .append(pad(level(r), 14)).append("  ")
                    .append(pad(result(r), 6)).append("  ")
                    .append(r.optional() ? "no (optional)" : "yes")
                    .append('\n');
        }

        sb.append("\n  Overall: ").append(outcome.buildPassed() ? "PASS" : "FAIL");
        if (!outcome.buildPassed()) {
            String failed = outcome.gatingFailures().stream().map(CombinationOutcome::run)
                    .reduce((a, b) -> a + ", " + b).orElse("");
            sb.append(" — failing: ").append(failed);
        }
        sb.append('\n');
        return sb.toString();
    }

    private static String level(CombinationOutcome r) {
        if (r.levelCriterion() == null) {
            return "-";
        }
        return r.levelCriterion();
    }

    private static String result(CombinationOutcome r) {
        if (!r.scored()) {
            return "ERROR";
        }
        return r.met() ? "PASS" : "FAIL";
    }

    private static String pad(String s, int width) {
        if (s.length() >= width) {
            return s;
        }
        return s + " ".repeat(width - s.length());
    }
}
