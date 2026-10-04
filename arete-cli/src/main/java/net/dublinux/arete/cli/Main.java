package net.dublinux.arete.cli;

import net.dublinux.arete.engine.BundleValidationException;
import net.dublinux.arete.engine.Engine;
import net.dublinux.arete.engine.Overrides;
import net.dublinux.arete.engine.gate.GateException;
import net.dublinux.arete.engine.gate.GateJob;
import net.dublinux.arete.engine.gate.GateResult;
import net.dublinux.arete.engine.report.ReportWriter;
import net.dublinux.arete.engine.report.ScoreDiff;
import net.dublinux.arete.engine.report.ScoreReport;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The {@code arete} command: score a spec, diff two, write a report, check a policy source.
 *
 * <p>Exit codes: {@code 0} everything passed; {@code 1} the check failed (a score under the threshold, a
 * regression, a spec that does not parse); {@code 2} the command or its configuration is wrong (bad options,
 * an unreadable file, a policy source that cannot be loaded or fails its pin). The decision is the exit
 * code: nothing here posts to a code host.
 */
public final class Main {
    static final int OK = 0;
    static final int FAILED = 1;
    static final int ERROR = 2;

    private Main() { }

    public static void main(String[] args) {
        // Quiet the logging library the OpenAPI parser uses; a real error still shows.
        if (System.getProperty("org.slf4j.simpleLogger.defaultLogLevel") == null) {
            System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "error");
        }
        System.exit(run(args, System.out, System.err, Path.of("")));
    }

    /** Runs a command; {@code cwd} is what relative paths are resolved against. Never calls {@code System.exit}. */
    public static int run(String[] args, PrintStream out, PrintStream err, Path cwd) {
        try {
            List<String> argv = List.of(args);
            if (argv.isEmpty() || argv.get(0).equals("help") || argv.get(0).equals("--help") || argv.get(0).equals("-h")) {
                out.print(USAGE);
                return argv.isEmpty() ? ERROR : OK;
            }
            if (argv.get(0).equals("--version") || argv.get(0).equals("version")) {
                String version = Main.class.getPackage().getImplementationVersion();
                out.println("arete " + (version == null ? "(development build)" : version));
                return OK;
            }
            String command = argv.get(0);
            Options options = Options.parse(argv.subList(1, argv.size()));
            if (options.has("--help")) {
                out.print(USAGE);
                return OK;
            }
            return switch (command) {
                case "score" -> score(options, out, cwd, false);
                case "report" -> score(options, out, cwd, true);
                case "diff" -> diff(options, out, cwd);
                case "gate" -> gate(options, out, cwd);
                case "policy" -> policy(options, out, cwd);
                default -> throw new UsageException("unknown command '" + command + "'");
            };
        } catch (UsageException e) {
            err.println("arete: " + e.getMessage());
            err.println("Run 'arete help' for usage.");
            return ERROR;
        } catch (BundleValidationException | GateException e) {
            err.println("arete: " + e.getMessage());
            return ERROR;
        } catch (IOException e) {
            err.println("arete: " + e.getMessage());
            return ERROR;
        }
    }

    // ---- score / report -----------------------------------------------------------------------------------

    private static int score(Options options, PrintStream out, Path cwd, boolean fullReport) throws IOException {
        List<String> files = options.positional;
        if (files.isEmpty()) throw new UsageException("name at least one spec file");
        Engine engine = engine(options, cwd);
        List<ScoreReport> reports = new ArrayList<>();
        for (String file : files) {
            Path path = cwd.resolve(file);
            reports.add(ScoreReport.of(engine, label(file), read(path), policyFor(options, cwd, path), overridesFor(options, cwd, path)));
        }
        String format = fullReport ? "md" : options.value("--format") == null ? "text" : options.value("--format");
        String rendered = switch (format) {
            case "text" -> ReportWriter.text(reports);
            case "json" -> ReportWriter.json(reports);
            case "md", "markdown" -> ReportWriter.markdown(reports, fullReport);
            case "sarif" -> ReportWriter.sarif(reports);
            default -> throw new UsageException("--format must be text, json, md or sarif");
        };
        emit(options, out, cwd, rendered);

        Double threshold = threshold(options);
        int code = OK;
        for (ScoreReport report : reports) {
            if (report.status().equals("ENGINE_ERROR")) return ERROR;
            if (!report.succeeded()) code = FAILED;
            else if (threshold != null && report.score() < threshold) code = FAILED;
            else if (options.value("--fail-under") != null && options.value("--fail-under").equals("policy") && !report.meetsPassingScore()) code = FAILED;
        }
        return code;
    }

    /** {@code --fail-under 85} is a number; {@code --fail-under policy} means the policy's own pass mark. */
    private static Double threshold(Options options) {
        String value = options.value("--fail-under");
        if (value == null || value.equals("policy")) return null;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new UsageException("--fail-under must be a number or 'policy'");
        }
    }

    // ---- diff ---------------------------------------------------------------------------------------------

    private static int diff(Options options, PrintStream out, Path cwd) throws IOException {
        if (options.positional.size() != 2) throw new UsageException("diff takes two specs: the base and the head");
        Engine engine = engine(options, cwd);
        Path basePath = cwd.resolve(options.positional.get(0));
        Path headPath = cwd.resolve(options.positional.get(1));
        // One policy and one set of overrides judge both, so a difference is the spec's, not the rules'.
        String policy = policyFor(options, cwd, headPath);
        Overrides overrides = overridesFor(options, cwd, headPath);
        ScoreReport base = ScoreReport.of(engine, label(options.positional.get(0)), read(basePath), policy, overrides);
        ScoreReport head = ScoreReport.of(engine, label(options.positional.get(1)), read(headPath), policy, overrides);
        ScoreDiff diff = ScoreDiff.of(base, head);

        String format = options.value("--format") == null ? "text" : options.value("--format");
        String rendered = switch (format) {
            case "text" -> ReportWriter.text(diff);
            case "json" -> ReportWriter.json(diff);
            case "md", "markdown" -> ReportWriter.markdown(diff);
            case "sarif" -> ReportWriter.sarif(diff);
            default -> throw new UsageException("--format must be text, json, md or sarif");
        };
        emit(options, out, cwd, rendered);

        if (base.status().equals("ENGINE_ERROR") || head.status().equals("ENGINE_ERROR")) return ERROR;
        if (!head.succeeded()) return FAILED;
        if (options.has("--fail-on-regression") && (diff.regressed() || diff.newBlockers() > 0)) return FAILED;
        return OK;
    }

    // ---- gate ---------------------------------------------------------------------------------------------

    private static int gate(Options options, PrintStream out, Path cwd) throws IOException {
        if (!options.positional.isEmpty()) throw new UsageException("gate takes no arguments; name the base with --target or --base-sha");
        GateJob.Config config = new GateJob.Config();
        config.repository = options.value("--repo") == null ? cwd : cwd.resolve(options.value("--repo"));
        config.target = options.value("--target");
        config.baseSha = options.value("--base-sha");
        config.globs.addAll(options.all("--paths"));
        config.policy = options.value("--policy");
        config.policySources.addAll(options.all("--policy-source"));
        config.mavenRepositories.addAll(options.all("--maven-repository"));
        String settings = options.value("--maven-settings");
        if (settings != null) {
            if (settings.equals("default")) config.useDefaultMavenSettings = true;
            else config.mavenSettingsFiles.add(cwd.resolve(settings));
        }
        config.mavenProfiles.addAll(options.all("--maven-profile"));
        config.requirePin = options.has("--require-pin");
        config.noCache = options.has("--no-cache");
        if (options.value("--cache-dir") != null) config.cacheDir = cwd.resolve(options.value("--cache-dir"));
        if (options.value("--user-policies") != null) config.userPoliciesDir = cwd.resolve(options.value("--user-policies"));
        String source = options.value("--base-source") == null ? "git" : options.value("--base-source");
        switch (source) {
            case "git" -> { }
            case "raw" -> {
                if (options.value("--raw-url") == null) throw new UsageException("--base-source raw needs --raw-url, a URL with {path} and {ref}");
                config.rawUrl = options.value("--raw-url");
                for (String header : options.all("--raw-header")) {
                    int colon = header.indexOf(':');
                    if (colon <= 0) throw new UsageException("--raw-header must be 'Name: value'");
                    config.rawHeaders.put(header.substring(0, colon).strip(), expandEnvironment(header.substring(colon + 1).strip()));
                }
                if (options.value("--changed-files") != null) {
                    config.changedFiles = Files.readAllLines(cwd.resolve(options.value("--changed-files"))).stream().map(String::strip).filter(l -> !l.isEmpty()).toList();
                }
            }
            default -> throw new UsageException("--base-source must be git or raw");
        }
        if (options.value("--report-json") != null) config.reportJson = cwd.resolve(options.value("--report-json"));
        if (options.value("--report-md") != null) config.reportMarkdown = cwd.resolve(options.value("--report-md"));
        if (options.value("--report-sarif") != null) config.reportSarif = cwd.resolve(options.value("--report-sarif"));
        config.reportOnly = options.has("--report-only");

        String format = options.value("--format") == null ? "text" : options.value("--format");
        if (!java.util.Set.of("text", "json", "md", "markdown", "sarif").contains(format)) throw new UsageException("--format must be text, json, md or sarif");

        GateJob.Outcome outcome = GateJob.run(config);
        GateResult result = outcome.result();
        emit(options, out, cwd, switch (format) {
            case "json" -> ReportWriter.json(result);
            case "md", "markdown" -> ReportWriter.markdown(result);
            case "sarif" -> ReportWriter.sarif(result);
            default -> ReportWriter.text(result);
        });

        if (outcome.engineError()) return ERROR;
        return outcome.blocks() ? FAILED : OK;   // --report-only: the files are written and nothing blocks
    }

    /** {@code $NAME} and {@code ${NAME}} in a header value come from the environment, so a token never sits in a script. */
    private static String expandEnvironment(String value) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\$\\{?([A-Za-z_][A-Za-z0-9_]*)}?").matcher(value);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String resolved = System.getenv(matcher.group(1));
            if (resolved == null) throw new UsageException("the environment variable " + matcher.group(1) + " (named in a --raw-header) is not set");
            matcher.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(resolved));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    // ---- policy verify ------------------------------------------------------------------------------------

    private static int policy(Options options, PrintStream out, Path cwd) {
        if (options.positional.size() != 1 || !options.positional.get(0).equals("verify")) {
            throw new UsageException("usage: arete policy verify [--policy-source ...]");
        }
        Engine engine = engine(options, cwd);   // loading it is the check: every source is fetched, pinned and compiled
        Engine.BundleInfo info = engine.bundleInfo();
        out.println("OK  " + (info.bundleId() == null ? "bundle" : info.bundleId()) + (info.bundleVersion() == null ? "" : " " + info.bundleVersion()));
        out.println("    " + info.policies().size() + " policies, " + info.rules() + " rules, " + info.matchers() + " matchers");
        for (String policy : info.policies()) out.println("    policy: " + policy);
        return OK;
    }

    // ---- shared -------------------------------------------------------------------------------------------

    private static Engine engine(Options options, Path cwd) {
        Engine.Builder builder = Engine.builder();
        for (String source : options.all("--policy-source")) builder.policySource(source);
        for (String repository : options.all("--maven-repository")) builder.mavenRepository(repository);
        String settings = options.value("--maven-settings");
        if (settings != null) {
            if (settings.equals("default")) builder.mavenSettings();
            else builder.mavenSettings(cwd.resolve(settings));
        }
        for (String profile : options.all("--maven-profile")) builder.mavenProfile(profile);
        builder.requirePin(options.has("--require-pin"));
        if (options.has("--no-cache")) builder.cacheDir(null);
        else if (options.value("--cache-dir") != null) builder.cacheDir(cwd.resolve(options.value("--cache-dir")));
        if (options.value("--user-policies") != null) builder.userPoliciesDir(cwd.resolve(options.value("--user-policies")));
        return builder.build();
    }

    /** {@code --policy}, else the one the folder's .arete.yaml names, else null (the engine's default). */
    private static String policyFor(Options options, Path cwd, Path spec) throws IOException {
        if (options.value("--policy") != null) return options.value("--policy");
        return overridesFor(options, cwd, spec).policy();
    }

    /** {@code --overrides file}, else the nearest {@code .arete.yaml} from the spec's folder upward, else none. */
    private static Overrides overridesFor(Options options, Path cwd, Path spec) throws IOException {
        if (options.has("--no-overrides")) return Overrides.none();
        if (options.value("--overrides") != null) return Overrides.parse(read(cwd.resolve(options.value("--overrides"))));
        for (Path dir = spec.toAbsolutePath().normalize().getParent(); dir != null; dir = dir.getParent()) {
            Path file = dir.resolve(".arete.yaml");
            if (Files.isRegularFile(file)) return Overrides.parse(read(file));
            if (Files.exists(dir.resolve(".git"))) break;   // the repository's top: do not read one from outside it
        }
        return Overrides.none();
    }

    private static String read(Path file) throws IOException {
        if (!Files.isRegularFile(file)) throw new IOException("cannot read " + file + ": no such file");
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private static String label(String file) { return file.replace('\\', '/'); }

    private static void emit(Options options, PrintStream out, Path cwd, String rendered) throws IOException {
        String target = options.value("--out");
        if (target == null) {
            out.print(rendered);
            return;
        }
        Path path = cwd.resolve(target);
        if (path.getParent() != null) Files.createDirectories(path.getParent());
        Files.writeString(path, rendered, StandardCharsets.UTF_8);
    }

    static final String USAGE = """
            arete: score API specs against a policy

            Usage:
              arete score  <spec>... [options]     score one or more specs
              arete diff   <base> <head> [options] score two versions of a spec and say what changed
              arete report <spec>... [options]     write a full markdown report
              arete gate [options]                 judge the specs a change touched against their base: the merge-gate
              arete policy verify [options]        check that the policy sources load, match their pins and compile
              arete help | --version

            Policy:
              --policy <name>               the policy to use (default: the .arete.yaml's, else the bundle's first)
              --policy-source <source>      a policy bundle; repeat to layer them. <uri>[#sha256=<hex>][&version=<v>]
                                            with classpath:, file:, https: or maven:group:artifact:version
              --maven-repository <url>      a Maven-layout repository (https or file:) for maven: sources
              --maven-settings <path|default>  read repositories, mirrors, credentials and proxies from settings.xml
              --maven-profile <id>          activate a settings.xml profile
              --require-pin                 refuse a remote policy source without a sha256
              --cache-dir <dir> | --no-cache   where fetched bundles are kept (default ~/.arete/cache/policies)
              --user-policies <dir>         extra *.md policies

            Overrides:
              .arete.yaml next to the spec (or in a parent folder) is read automatically.
              --overrides <file>            use this file instead
              --no-overrides                ignore any .arete.yaml

            Output:
              --format text|json|md|sarif   (default text)
              --out <file>                  write the output to a file instead of the terminal

            Gate (run in a git repository; the specs a change touched, each against its base):
              --target <ref>                the branch it merges into (default origin/main); the base is the merge-base
              --base-sha <sha>              the base commit itself (e.g. GitLab CI_MERGE_REQUEST_DIFF_BASE_SHA)
              --paths <glob>                spec files to look at, repeatable (default **/openapi.yaml, .yml, .json)
              --base-source git|raw         git reads the base with git show (needs history); raw fetches it from the code host
              --raw-url <url>               raw mode: a URL with {path} and {ref}; --raw-header 'Name: $TOKEN' (repeatable)
              --changed-files <file>        raw mode: the files to consider, one per line (default: every spec found)
              --report-json|--report-md|--report-sarif <file>   write these as well as the main output
              --report-only                 write the files and always exit 0: a trial before enforcement
              --repo <dir>                  the repository (default: the current directory)

            Checks (the exit code carries the decision):
              --fail-under <n|policy>       score: exit 1 if the score is below n, or below the policy's pass mark
              --fail-on-regression          diff: exit 1 if the score fell or a new blocker appeared

            Exit codes: 0 ok, 1 the check failed, 2 the command or its configuration is wrong
            (including a base that cannot be reached, which names the setting to fix).
            """;
}
