package net.dublinux.arete.engine.gate;

import net.dublinux.arete.engine.Engine;
import net.dublinux.arete.engine.Overrides;
import net.dublinux.arete.engine.report.ScoreReport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Runs the merge-gate over a repository: finds the specs, reads each one's base (the spec as it was at
 * the merge-base), scores both with the same policy and overrides, and applies {@link GateRules}.
 *
 * <p>In git mode the changed specs come from {@code git diff} against the base commit and the base from
 * {@code git show}: no checkout. Every spec matching the globs is scored on every run; one whose text is the base's is
 * judged on its own (see {@link GateRules}). In raw mode (a shallow clone) each spec's base is fetched from the code host.
 */
public final class GateRunner {
    private GateRunner() { }

    public static GateResult run(Engine engine, Path repository, GateRequest request) {
        Path root = repository.toAbsolutePath().normalize();
        BaseSource source;
        Map<String, String> candidates = new TreeMap<>();   // head path -> path at the base (differs for a rename)
        if (request.mode == GateRequest.Mode.GIT) {
            Git git = new Git(root);
            String base = resolveBase(git, request);
            source = new GitBaseSource(git, base);
            for (String path : walk(root, request)) candidates.put(path, path);   // every spec is scored, changed or not
            changedByGit(git, base, request, candidates);                          // and a renamed one is read at its old path
        } else {
            source = new RawBaseSource(request.rawUrl, request.baseSha, request.rawHeaders, request.http);
            for (String path : request.changedFiles != null ? filter(request.changedFiles, request) : walk(root, request)) candidates.put(path, path);
        }

        List<SpecResult> results = new ArrayList<>();
        for (Map.Entry<String, String> candidate : candidates.entrySet()) {
            String file = candidate.getKey();
            Path path = root.resolve(file);
            if (!Files.isRegularFile(path)) continue;   // deleted
            String headText = read(path);
            Optional<String> baseText = source.read(candidate.getValue());
            boolean unchanged = baseText.isPresent() && baseText.get().equals(headText);
            Overrides overrides = Overrides.discover(path, root);
            String policy = request.policy != null ? request.policy : overrides.policy();
            ScoreReport head = ScoreReport.of(engine, file, headText, policy, overrides);
            ScoreReport base = null;
            String warning = null;
            if (baseText.isPresent() && !unchanged) {
                base = ScoreReport.of(engine, file, baseText.get(), policy, overrides);
                if (!base.succeeded()) {
                    warning = "the base did not parse (" + base.errorMessage() + "), so this is judged as a new spec";
                    base = null;
                }
            }
            results.add(GateRules.evaluate(file, head, base, warning, unchanged));
        }
        return new GateResult(source.description(), results);
    }

    // ---- git ----------------------------------------------------------------------------------------------

    private static String resolveBase(Git git, GateRequest request) {
        if (!git.run("rev-parse", "--is-inside-work-tree").ok()) throw new GateException("not inside a git repository");
        boolean shallow = git.run("rev-parse", "--is-shallow-repository").out().strip().equals("true");
        String hint = shallow
                ? " The clone is shallow: fetch more history (GIT_DEPTH: 0 on GitLab, fetch-depth: 0 on GitHub), or give the base commit with --base-sha, or read the base from the code host with --base-source raw."
                : "";
        String sha;
        if (request.baseSha != null && !request.baseSha.isBlank()) {
            sha = request.baseSha.strip();
        } else {
            Git.Result mergeBase = git.run("merge-base", "HEAD", request.target);
            if (!mergeBase.ok()) {
                boolean known = git.run("rev-parse", "--verify", "--quiet", request.target + "^{commit}").ok();
                throw new GateException(known
                        ? "no common ancestor of HEAD and " + request.target + " was found." + hint
                        : "the target " + request.target + " is not in this clone; fetch it (git fetch origin <branch>) or give --target / --base-sha." + hint);
            }
            sha = mergeBase.out().strip();
        }
        if (!git.run("cat-file", "-e", sha + "^{commit}").ok()) {
            throw new GateException("the base commit " + sha + " is not in this clone." + hint);
        }
        return sha;
    }

    private static void changedByGit(Git git, String base, GateRequest request, Map<String, String> into) {
        Git.Result diff = git.run("diff", "--name-status", "-M", base);
        if (!diff.ok()) throw new GateException("git diff against " + base + " failed: " + diff.err().strip());
        for (String line : diff.out().split("\n")) {
            if (line.isBlank()) continue;
            String[] parts = line.split("\t");
            char status = parts[0].charAt(0);
            if (status == 'D') continue;
            String path = parts[parts.length - 1];
            String oldPath = (status == 'R' || status == 'C') && parts.length >= 3 ? parts[1] : path;
            if (matches(path, request.globs)) into.put(path, oldPath);
        }
        // Files not yet added to git are new specs too (a developer running the gate before committing).
        Git.Result untracked = git.run("ls-files", "--others", "--exclude-standard");
        if (untracked.ok()) {
            for (String path : untracked.out().split("\n")) {
                if (!path.isBlank() && matches(path, request.globs)) into.putIfAbsent(path, path);
            }
        }
    }

    // ---- selecting specs ----------------------------------------------------------------------------------

    private static List<String> filter(List<String> files, GateRequest request) {
        List<String> kept = new ArrayList<>();
        for (String file : files) if (matches(file.replace('\\', '/'), request.globs)) kept.add(file.replace('\\', '/'));
        return kept;
    }

    private static boolean matches(String path, List<String> globs) {
        for (String glob : globs) {
            PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
            // "**/openapi.yaml" should match a file at the repository root too.
            if (matcher.matches(Path.of(path)) || (glob.startsWith("**/") && FileSystems.getDefault().getPathMatcher("glob:" + glob.substring(3)).matches(Path.of(path)))) return true;
        }
        return false;
    }

    private static List<String> walk(Path root, GateRequest request) {
        List<String> found = new ArrayList<>();
        Set<String> skipDirs = Set.of(".git", "node_modules", "target", "build", ".gradle");
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    return !dir.equals(root) && skipDirs.contains(dir.getFileName().toString()) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    String relative = root.relativize(file).toString().replace('\\', '/');
                    if (matches(relative, request.globs)) found.add(relative);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new GateException("could not list the specs under " + root + ": " + e.getMessage());
        }
        return found;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new GateException("could not read " + path + ": " + e.getMessage());
        }
    }
}
