package net.dublinux.arete.scoring.policy;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Keeps the editor TextMate grammar's builtin lists in lockstep with the
 * interpreter. {@code editors/distill/distill.tmLanguage.json} is hand-written
 * and checked in; this test owns only the two {@code @generated} alternations —
 * the bundled functions and the bundled members.
 *
 * <p>After adding a function or member to {@link DistillMatcherEvaluator},
 * regenerate and commit the grammar:
 * <pre>
 * mvn -pl arete-policy-plugin test -Dtest=DistillGrammarSnapshotTest -Dsnapshot.update=true
 * </pre>
 */
class DistillGrammarSnapshotTest {

    private static final boolean UPDATE = Boolean.getBoolean("snapshot.update");
    private static final Path GRAMMAR = Path.of("..", "editors", "distill", "distill.tmLanguage.json");

    @Test
    void grammarBuiltinListsMatchTheInterpreter() throws Exception {
        assertTrue(Files.exists(GRAMMAR), "missing " + GRAMMAR.toAbsolutePath());

        String grammar = Files.readString(GRAMMAR);
        String updated = grammar;
        updated = rewriteAlternation(updated, "@generated distill.function.names",
                DistillMatcherEvaluator.KNOWN_FUNCTIONS);
        updated = rewriteAlternation(updated, "@generated distill.member.names",
                DistillMatcherEvaluator.KNOWN_MEMBERS);

        if (updated.equals(grammar)) {
            return;
        }
        if (UPDATE) {
            Files.writeString(GRAMMAR, updated);
            return;
        }
        fail("editors/distill/distill.tmLanguage.json is stale — regenerate:\n"
                + "  mvn -pl arete-policy-plugin test -Dtest=DistillGrammarSnapshotTest -Dsnapshot.update=true");
    }

    /**
     * Replaces the {@code (a|b|c)} alternation inside the {@code "match"} value
     * of the repository rule carrying {@code marker} in its {@code "comment"}
     * with the sorted members of {@code names}. Names are plain identifiers, so
     * no regex quoting is required.
     */
    private static String rewriteAlternation(String grammar, String marker, Set<String> names) {
        int markerAt = grammar.indexOf(marker);
        if (markerAt < 0) throw new IllegalStateException("grammar has no rule marked '" + marker + "'");
        int matchAt = grammar.indexOf("\"match\"", markerAt);
        if (matchAt < 0) throw new IllegalStateException("no \"match\" after '" + marker + "'");
        int open = grammar.indexOf('"', grammar.indexOf(':', matchAt) + 1);
        int close = grammar.indexOf('"', open + 1);
        String matchValue = grammar.substring(open + 1, close);

        Matcher alt = Pattern.compile("\\(([A-Za-z][A-Za-z|]*)\\)").matcher(matchValue);
        if (!alt.find()) throw new IllegalStateException("no alternation in match: " + matchValue);

        String wanted = new TreeSet<>(names).stream().collect(Collectors.joining("|"));
        return grammar.replace("(" + alt.group(1) + ")", "(" + wanted + ")");
    }
}
