package net.dublinux.arete.engine.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.dublinux.arete.engine.Overrides;
import net.dublinux.arete.engine.gate.GateResult;
import net.dublinux.arete.engine.gate.SpecResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders reports as plain text (a terminal), JSON (machines), markdown (a merge-request comment or a file
 * to keep) and SARIF (code-scanning annotations). Nothing here talks to a code host: a separate CI step
 * posts the text.
 */
public final class ReportWriter {
    public static final int JSON_SCHEMA_VERSION = 1;

    /** How many unchanged findings a diff comment lists before it says how many it left out. */
    static final int MAX_EXISTING_IN_COMMENT = 20;

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private ReportWriter() { }

    // ---- text ---------------------------------------------------------------------------------------------

    public static String text(List<ScoreReport> reports) {
        StringBuilder out = new StringBuilder();
        for (ScoreReport report : reports) {
            out.append(headline(report)).append('\n');
            if (!report.succeeded()) {
                out.append("  ").append(report.status()).append(": ").append(report.errorMessage()).append('\n');
            }
            for (Finding finding : report.findings()) {
                out.append("  ").append(where(report, finding)).append("  ").append(finding.severityLabel()).append("  ")
                        .append(finding.ruleId()).append("  ").append(finding.message()).append('\n');
            }
            for (Overrides.RuleOverride override : report.overridesApplied()) {
                out.append("  override ").append(override.ruleId()).append(": ").append(override.reason()).append('\n');
            }
        }
        return out.toString();
    }

    public static String text(ScoreDiff diff) {
        StringBuilder out = new StringBuilder(diffHeadline(diff)).append('\n');
        for (ScoreDiff.Kind kind : ScoreDiff.Kind.values()) {
            for (ScoreDiff.Change change : diff.of(kind)) {
                ScoreReport source = kind == ScoreDiff.Kind.RESOLVED ? diff.base() : diff.head();
                out.append("  ").append(String.format(Locale.ROOT, "%-8s", kind)).append(' ').append(change.finding().severityLabel())
                        .append("  ").append(change.finding().ruleId()).append("  ").append(where(source, change.finding()))
                        .append("  ").append(change.finding().message()).append('\n');
            }
        }
        return out.toString();
    }

    // ---- markdown -----------------------------------------------------------------------------------------

    /** A summary table per spec; {@code full} also groups every finding under its rule and lists the overrides applied. */
    public static String markdown(List<ScoreReport> reports, boolean full) {
        StringBuilder out = new StringBuilder();
        for (ScoreReport report : reports) {
            out.append("## Areté: ").append(report.file()).append("\n\n").append(headline(report)).append("\n\n");
            if (!report.succeeded()) {
                out.append("> ").append(report.status()).append(": ").append(report.errorMessage()).append("\n\n");
                continue;
            }
            if (report.findings().isEmpty()) {
                out.append("No findings.\n\n");
            } else if (full) {
                Map<String, List<Finding>> byRule = new LinkedHashMap<>();
                for (Finding f : report.findings()) byRule.computeIfAbsent(f.ruleId(), k -> new ArrayList<>()).add(f);
                for (Map.Entry<String, List<Finding>> rule : byRule.entrySet()) {
                    Finding first = rule.getValue().get(0);
                    out.append("### ").append(rule.getKey()).append(" — ").append(first.title()).append("\n\n");
                    out.append(first.severityLabel()).append(", ").append(rule.getValue().size()).append(rule.getValue().size() == 1 ? " finding" : " findings")
                            .append(first.scoreImpact() > 0 ? ", costs " + number(first.scoreImpact()) + " once" : "").append("\n\n");
                    for (Finding f : rule.getValue()) {
                        out.append("- ").append(where(report, f)).append(" `").append(f.pointer()).append("` — ").append(oneLine(f.message())).append('\n');
                    }
                    out.append('\n');
                }
            } else {
                out.append("| Where | Severity | Rule | Message |\n| --- | --- | --- | --- |\n");
                for (Finding f : report.findings()) {
                    out.append("| ").append(where(report, f)).append(" | ").append(f.severityLabel()).append(" | `").append(f.ruleId())
                            .append("` | ").append(cell(f.message())).append(" |\n");
                }
                out.append('\n');
            }
            appendOverrides(out, report);
        }
        return out.toString();
    }

    /**
     * The merge-request comment: a score line, then one line per finding —
     * {@code NEW|EXISTING|RESOLVED}, severity, rule, pointer, message — new ones first.
     */
    public static String markdown(ScoreDiff diff) {
        StringBuilder out = new StringBuilder("## Areté: ").append(diff.head().file()).append("\n\n")
                .append(diffHeadline(diff)).append("\n\n");
        if (!diff.head().succeeded()) {
            out.append("> ").append(diff.head().status()).append(": ").append(diff.head().errorMessage()).append("\n\n");
            return out.toString();
        }
        int existingShown = 0;
        long existing = diff.count(ScoreDiff.Kind.EXISTING);
        for (ScoreDiff.Kind kind : ScoreDiff.Kind.values()) {
            for (ScoreDiff.Change change : diff.of(kind)) {
                if (kind == ScoreDiff.Kind.EXISTING && existingShown++ >= MAX_EXISTING_IN_COMMENT) continue;
                ScoreReport source = kind == ScoreDiff.Kind.RESOLVED ? diff.base() : diff.head();
                Finding f = change.finding();
                out.append("- **").append(kind).append("** ").append(f.severityLabel()).append(" `").append(f.ruleId()).append("` `")
                        .append(f.pointer()).append("` (").append(where(source, f)).append(") — ").append(oneLine(f.message())).append('\n');
            }
        }
        if (existing > MAX_EXISTING_IN_COMMENT) {
            out.append("- … and ").append(existing - MAX_EXISTING_IN_COMMENT).append(" more existing findings (see the full report)\n");
        }
        if (diff.changes().isEmpty()) out.append("No findings.\n");
        out.append('\n');
        appendOverrides(out, diff.head());
        return out.toString();
    }

    private static void appendOverrides(StringBuilder out, ScoreReport report) {
        if (report.overridesApplied().isEmpty()) return;
        out.append("Overrides applied:\n\n");
        for (Overrides.RuleOverride o : report.overridesApplied()) {
            out.append("- `").append(o.ruleId()).append("` — ").append(oneLine(o.reason())).append('\n');
        }
        out.append('\n');
    }

    // ---- json ---------------------------------------------------------------------------------------------

    public static String json(List<ScoreReport> reports) {
        ObjectNode root = envelope("score");
        ArrayNode specs = root.putArray("specs");
        for (ScoreReport report : reports) specs.add(reportNode(report));
        return write(root);
    }

    public static String json(ScoreDiff diff) {
        ObjectNode root = envelope("diff");
        root.put("file", diff.head().file());
        root.set("base", reportNode(diff.base()));
        root.set("head", reportNode(diff.head()));
        root.put("scoreDelta", diff.scoreDelta());
        root.put("regressed", diff.regressed());
        ObjectNode counts = root.putObject("counts");
        for (ScoreDiff.Kind kind : ScoreDiff.Kind.values()) counts.put(kind.name().toLowerCase(Locale.ROOT), diff.count(kind));
        ArrayNode changes = root.putArray("changes");
        for (ScoreDiff.Change change : diff.changes()) {
            ObjectNode node = findingNode(change.finding());
            node.put("kind", change.kind().name());
            changes.add(node);
        }
        return write(root);
    }

    private static ObjectNode envelope(String kind) {
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", JSON_SCHEMA_VERSION);
        root.put("kind", kind);
        root.putObject("tool").put("name", "Areté");
        return root;
    }

    private static ObjectNode reportNode(ScoreReport report) {
        ObjectNode node = JSON.createObjectNode();
        node.put("file", report.file());
        node.put("policy", report.policy());
        node.put("status", report.status());
        if (report.errorMessage() != null) node.put("errorMessage", report.errorMessage());
        if (report.succeeded()) {
            node.put("score", report.score());
            node.put("scoreWithoutBlockers", report.scoreWithoutBlockers());
            if (report.grade() != null) node.put("grade", report.grade());
            if (report.passingScore() != null) node.put("passingScore", report.passingScore());
            node.put("meetsPassingScore", report.meetsPassingScore());
            node.put("rulesEvaluated", report.rulesEvaluated());
        }
        ArrayNode findings = node.putArray("findings");
        for (Finding finding : report.findings()) findings.add(findingNode(finding));
        ArrayNode rules = node.putArray("rules");
        for (net.dublinux.arete.engine.api.RuleOutcome outcome : report.ruleOutcomes()) {
            ObjectNode r = rules.addObject();
            r.put("rule", outcome.ruleId());
            r.put("count", outcome.count());
            r.put("cost", outcome.cost());
            r.put("graduated", outcome.graduated());
        }
        ArrayNode overrides = node.putArray("overridesApplied");
        for (Overrides.RuleOverride o : report.overridesApplied()) {
            ObjectNode n = overrides.addObject();
            n.put("rule", o.ruleId());
            n.put("reason", o.reason());
            n.put("disabled", o.disabled());
            if (o.points() != null) n.put("points", o.points());
        }
        return node;
    }

    private static ObjectNode findingNode(Finding f) {
        ObjectNode n = JSON.createObjectNode();
        n.put("rule", f.ruleId());
        n.put("title", f.title());
        n.put("severity", f.severity());
        n.put("severityLabel", f.severityLabel());
        if (f.pointer() != null) n.put("pointer", f.pointer());
        if (f.path() != null) n.put("path", f.path());
        n.put("message", f.message());
        if (f.line() != null) n.put("line", f.line());
        if (f.column() != null) n.put("column", f.column());
        n.put("scoreImpact", f.scoreImpact());
        return n;
    }

    // ---- sarif --------------------------------------------------------------------------------------------

    /** SARIF 2.1.0 for the reports' findings, with the file and line of each so a code host can annotate it. */
    public static String sarif(List<ScoreReport> reports) {
        ObjectNode root = sarifRoot();
        ArrayNode results = ((ObjectNode) root.withArray("runs").get(0)).putArray("results");
        Map<String, Finding> rules = new LinkedHashMap<>();
        for (ScoreReport report : reports) {
            for (Finding f : report.findings()) {
                rules.putIfAbsent(f.ruleId(), f);
                results.add(sarifResult(report.file(), f));
            }
        }
        addSarifRules(root, rules);
        return write(root);
    }

    /** SARIF for the findings a change introduced (NEW only), the ones worth annotating on a merge request. */
    public static String sarif(ScoreDiff diff) {
        ObjectNode root = sarifRoot();
        ArrayNode results = ((ObjectNode) root.withArray("runs").get(0)).putArray("results");
        Map<String, Finding> rules = new LinkedHashMap<>();
        for (ScoreDiff.Change change : diff.of(ScoreDiff.Kind.NEW)) {
            rules.putIfAbsent(change.finding().ruleId(), change.finding());
            results.add(sarifResult(diff.head().file(), change.finding()));
        }
        addSarifRules(root, rules);
        return write(root);
    }

    private static ObjectNode sarifRoot() {
        ObjectNode root = JSON.createObjectNode();
        root.put("version", "2.1.0");
        root.put("$schema", "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json");
        ObjectNode run = root.putArray("runs").addObject();
        run.putObject("tool").putObject("driver").put("name", "Areté").put("informationUri", "https://johnjoeallen.github.io/arete/");
        return root;
    }

    private static void addSarifRules(ObjectNode root, Map<String, Finding> rules) {
        ArrayNode driverRules = ((ObjectNode) ((ObjectNode) root.withArray("runs").get(0)).path("tool").path("driver")).putArray("rules");
        for (Finding f : rules.values()) {
            ObjectNode rule = driverRules.addObject();
            rule.put("id", f.ruleId());
            rule.put("name", f.title());
            rule.putObject("shortDescription").put("text", f.title());
        }
    }

    private static ObjectNode sarifResult(String file, Finding f) {
        ObjectNode result = JSON.createObjectNode();
        result.put("ruleId", f.ruleId());
        result.put("level", switch (f.severity()) { case "ERROR" -> "error"; case "WARNING" -> "warning"; default -> "note"; });
        result.putObject("message").put("text", f.message());
        ObjectNode location = result.putArray("locations").addObject();
        ObjectNode physical = location.putObject("physicalLocation");
        physical.putObject("artifactLocation").put("uri", file.replace('\\', '/'));
        if (f.line() != null) {
            ObjectNode region = physical.putObject("region");
            region.put("startLine", f.line());
            if (f.column() != null) region.put("startColumn", f.column());
        }
        if (f.pointer() != null) location.putArray("logicalLocations").addObject().put("fullyQualifiedName", f.pointer());
        return result;
    }

    // ---- gate ---------------------------------------------------------------------------------------------

    public static String text(GateResult gate) {
        StringBuilder out = new StringBuilder("gate: ").append(gate.passed() ? "PASSED" : "FAILED").append("  ").append(gate.specs().size())
                .append(gate.specs().size() == 1 ? " spec" : " specs").append(" checked, ").append(gate.skipped().size()).append(" unchanged  (base ")
                .append(gate.baseDescription()).append(")\n");
        for (SpecResult spec : gate.specs()) {
            out.append(spec.passed() ? "  PASS  " : "  FAIL  ").append(spec.file()).append(spec.isNew() ? "  (new spec)" : "").append('\n');
            for (String reason : spec.reasons()) out.append("        ").append(reason).append('\n');
            if (spec.warning() != null) out.append("        note: ").append(spec.warning()).append('\n');
        }
        return out.toString();
    }

    /** The merge-request comment: the verdict and why, then each spec's score line and one line per finding, new first. */
    public static String markdown(GateResult gate) {
        StringBuilder out = new StringBuilder("## Areté gate: ").append(gate.passed() ? "PASSED" : "FAILED").append("\n\n")
                .append(gate.specs().size()).append(gate.specs().size() == 1 ? " spec" : " specs").append(" checked");
        if (!gate.skipped().isEmpty()) out.append(", ").append(gate.skipped().size()).append(" unchanged");
        out.append(". Base: ").append(gate.baseDescription()).append(".\n\n");
        if (!gate.passed()) {
            out.append("**Why it failed**\n\n");
            for (String reason : gate.reasons()) out.append(reason.startsWith("  ") ? "  - " + reason.strip() : "- " + reason).append('\n');
            out.append('\n');
        }
        for (SpecResult spec : gate.specs()) {
            out.append("### ").append(spec.file()).append(" — ").append(spec.isNew() ? "new spec, " : "").append(spec.passed() ? "passed" : "failed").append("\n\n");
            if (spec.warning() != null) out.append("_Note: ").append(spec.warning()).append("._\n\n");
            if (!spec.head().succeeded()) {
                out.append("> ").append(spec.head().status()).append(": ").append(spec.head().errorMessage()).append("\n\n");
                continue;
            }
            if (spec.isNew()) {
                out.append(headline(spec.head())).append("\n\n");
                for (Finding f : spec.head().findings()) {
                    out.append("- **NEW** ").append(f.severityLabel()).append(" `").append(f.ruleId()).append("` `").append(f.pointer()).append("` (")
                            .append(where(spec.head(), f)).append(") — ").append(oneLine(f.message())).append('\n');
                }
                if (spec.head().findings().isEmpty()) out.append("No findings.\n");
                out.append('\n');
            } else {
                String comment = markdown(spec.diff());
                out.append(comment.substring(comment.indexOf("\n\n") + 2).stripLeading()).append('\n');
            }
        }
        return out.toString();
    }

    public static String json(GateResult gate) {
        ObjectNode root = envelope("gate");
        root.put("passed", gate.passed());
        root.put("base", gate.baseDescription());
        ArrayNode reasons = root.putArray("reasons");
        for (String reason : gate.reasons()) reasons.add(reason);
        ArrayNode skipped = root.putArray("unchanged");
        for (String file : gate.skipped()) skipped.add(file);
        ArrayNode specs = root.putArray("specs");
        for (SpecResult spec : gate.specs()) {
            ObjectNode node = specs.addObject();
            node.put("file", spec.file());
            node.put("newSpec", spec.isNew());
            node.put("passed", spec.passed());
            ArrayNode why = node.putArray("reasons");
            for (String reason : spec.reasons()) why.add(reason);
            if (spec.warning() != null) node.put("warning", spec.warning());
            node.set("head", reportNode(spec.head()));
            if (spec.base() != null) node.set("base", reportNode(spec.base()));
            if (spec.diff() != null) {
                node.put("scoreDelta", spec.diff().scoreDelta());
                ObjectNode counts = node.putObject("counts");
                for (ScoreDiff.Kind kind : ScoreDiff.Kind.values()) counts.put(kind.name().toLowerCase(Locale.ROOT), spec.diff().count(kind));
                ArrayNode changes = node.putArray("changes");
                for (ScoreDiff.Change change : spec.diff().changes()) {
                    ObjectNode c = findingNode(change.finding());
                    c.put("kind", change.kind().name());
                    changes.add(c);
                }
            }
        }
        return write(root);
    }

    /** SARIF of what the change introduced: the new findings of changed specs, and every finding of a new spec. */
    public static String sarif(GateResult gate) {
        ObjectNode root = sarifRoot();
        ArrayNode results = ((ObjectNode) root.withArray("runs").get(0)).putArray("results");
        Map<String, Finding> rules = new LinkedHashMap<>();
        for (SpecResult spec : gate.specs()) {
            List<Finding> introduced = new ArrayList<>();
            if (spec.isNew()) introduced.addAll(spec.head().findings());
            else if (spec.diff() != null) for (ScoreDiff.Change change : spec.diff().of(ScoreDiff.Kind.NEW)) introduced.add(change.finding());
            for (Finding f : introduced) {
                rules.putIfAbsent(f.ruleId(), f);
                results.add(sarifResult(spec.file(), f));
            }
        }
        addSarifRules(root, rules);
        return write(root);
    }

    // ---- shared -------------------------------------------------------------------------------------------

    private static String headline(ScoreReport report) {
        if (!report.succeeded()) return report.file() + "  " + report.policy() + "  " + report.status();
        StringBuilder out = new StringBuilder(report.file()).append("  ").append(report.policy()).append("  score ").append(number(report.score()));
        if (report.grade() != null) out.append(" (").append(report.grade()).append(')');
        if (report.passingScore() != null) {
            out.append("  pass mark ").append(number(report.passingScore())).append(": ").append(report.meetsPassingScore() ? "PASS" : "FAIL");
        }
        long blockers = report.findings().stream().filter(f -> "ERROR".equals(f.severity())).count();
        out.append("  ").append(report.findings().size()).append(report.findings().size() == 1 ? " finding" : " findings");
        if (blockers > 0) out.append(", ").append(blockers).append(blockers == 1 ? " blocker" : " blockers");
        return out.toString();
    }

    private static String diffHeadline(ScoreDiff diff) {
        StringBuilder out = new StringBuilder(diff.head().file()).append("  ").append(diff.head().policy());
        if (diff.base().succeeded() && diff.head().succeeded()) {
            double delta = diff.scoreDelta();
            out.append("  score ").append(number(diff.base().score())).append(" -> ").append(number(diff.head().score()))
                    .append(" (").append(delta >= 0 ? "+" : "").append(number(delta)).append(')');
            if (diff.head().passingScore() != null) {
                out.append("  pass mark ").append(number(diff.head().passingScore())).append(": ").append(diff.head().meetsPassingScore() ? "PASS" : "FAIL");
            }
        } else {
            out.append("  ").append(diff.head().succeeded() ? diff.base().status() : diff.head().status());
        }
        out.append("  ").append(diff.count(ScoreDiff.Kind.NEW)).append(" new, ").append(diff.count(ScoreDiff.Kind.EXISTING))
                .append(" existing, ").append(diff.count(ScoreDiff.Kind.RESOLVED)).append(" resolved");
        return out.toString();
    }

    private static String where(ScoreReport report, Finding f) {
        return f.line() == null ? report.file() : report.file() + ":" + f.line();
    }

    private static String number(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.1f", value);
    }

    private static String oneLine(String text) { return text == null ? "" : text.replaceAll("\\s+", " ").strip(); }

    private static String cell(String text) { return oneLine(text).replace("|", "\\|"); }

    private static String write(ObjectNode node) {
        try {
            return JSON.writeValueAsString(node) + "\n";
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
