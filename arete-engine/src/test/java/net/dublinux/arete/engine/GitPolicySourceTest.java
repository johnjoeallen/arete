package net.dublinux.arete.engine;

import net.dublinux.arete.engine.api.ScoringResult;
import net.dublinux.arete.engine.api.SpecFormat;
import net.dublinux.arete.engine.api.SpecInput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** A policy bundle can live in a git repository: any ref, a folder in it, and the same optional pins as any source. */
class GitPolicySourceTest {
    @BeforeAll
    static void gitIsAvailable() {
        boolean available;
        try {
            available = new ProcessBuilder("git", "--version").start().waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            available = false;
        }
        assumeTrue(available, "the git program is not installed");
    }

    private static String git(Path dir, String... args) throws IOException {
        java.util.List<String> command = new java.util.ArrayList<>(List.of("git", "-c", "user.name=T", "-c", "user.email=t@example.com", "-c", "commit.gpgsign=false", "-c", "init.defaultBranch=main"));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).directory(dir.toFile()).redirectErrorStream(true).start();
        String out = new String(process.getInputStream().readAllBytes());
        try {
            if (process.waitFor() != 0) throw new IllegalStateException("git " + String.join(" ", args) + ": " + out);
        } catch (InterruptedException e) {
            throw new IllegalStateException(e);
        }
        return out.strip();
    }

    /** A repository with the bundle in {@code policy/} at version 1.0.0 (tagged v1) and then 2.0.0 on main. */
    private static Path repository(Path tmp) throws IOException {
        Path repo = Files.createDirectories(tmp.resolve("repo"));
        git(repo, "init", "-q");
        MiniBundle.writeDir(repo.resolve("policy"), MiniBundle.base("1.0.0"));
        Files.writeString(repo.resolve("README.md"), "not part of the bundle");
        git(repo, "add", "-A");
        git(repo, "commit", "-q", "-m", "one");
        git(repo, "tag", "v1");
        MiniBundle.writeDir(repo.resolve("policy"), MiniBundle.base("2.0.0"));
        git(repo, "commit", "-q", "-am", "two");
        return repo;
    }

    private static String url(Path repo) { return "git:file://" + repo.toAbsolutePath(); }

    private static Engine engine(String source, Path cache) {
        return Engine.builder().policySource(source).cacheDir(cache).build();
    }

    private static String version(Engine engine) { return engine.bundleInfo().bundleVersion(); }

    @Test
    void aSourceParsesItsGitPinsAndRefusesThemElsewhere() {
        PolicySource source = PolicySource.parse("git:https://host/org/policy.git#ref=v1.2&path=bundle&version=1.2.0");
        assertEquals("git:https://host/org/policy.git", source.uri());
        assertEquals("v1.2", source.ref());
        assertEquals("bundle", source.path());
        assertEquals("git:https://host/org/policy.git#version=1.2.0&ref=v1.2&path=bundle", source.toString());
        assertThrows(BundleValidationException.class, () -> PolicySource.parse("https://host/policy.zip#ref=main"));
    }

    @Test
    void theDefaultBranchIsUsedWhenNoRefIsGiven(@TempDir Path tmp) throws IOException {
        Path repo = repository(tmp);

        Engine engine = engine(url(repo) + "#path=policy", null);

        assertEquals("2.0.0", version(engine));
        assertEquals(List.of("Mini"), engine.getPolicies());
    }

    @Test
    void aTagAndACommitPickAnEarlierBundle(@TempDir Path tmp) throws IOException {
        Path repo = repository(tmp);
        String first = git(repo, "rev-parse", "v1");

        assertEquals("1.0.0", version(engine(url(repo) + "#ref=v1&path=policy", null)));
        assertEquals("1.0.0", version(engine(url(repo) + "#ref=" + first + "&path=policy", null)));
    }

    @Test
    void theBundleMayBeAtTheRootOfTheRepository(@TempDir Path tmp) throws IOException {
        Path repo = Files.createDirectories(tmp.resolve("root"));
        git(repo, "init", "-q");
        MiniBundle.writeDir(repo, MiniBundle.base("3.0.0"));
        git(repo, "add", "-A");
        git(repo, "commit", "-q", "-m", "bundle");

        assertEquals("3.0.0", version(engine(url(repo), null)));
    }

    @Test
    void aShaPinMatchesTheSameFilesAndIsCachedForAnOfflineRun(@TempDir Path tmp) throws IOException {
        Path repo = repository(tmp);
        String source = url(repo) + "#path=policy";
        // The first run, unpinned, tells us nothing about the digest; ask the error message for it.
        BundleValidationException probe = assertThrows(BundleValidationException.class,
                () -> engine(source + "&sha256=" + "0".repeat(64), tmp.resolve("cache")));
        String digest = probe.getMessage().substring(probe.getMessage().lastIndexOf("it is ") + 6).strip();

        Engine online = engine(source + "&sha256=" + digest, tmp.resolve("cache"));
        assertEquals("2.0.0", version(online));

        deleteRecursively(repo);
        Engine offline = engine(source + "&sha256=" + digest, tmp.resolve("cache"));
        assertEquals("2.0.0", version(offline), "the cached archive is used without the repository");
    }

    @Test
    void theSameFilesGiveTheSameDigestWhateverTheCommit(@TempDir Path tmp) throws IOException {
        Path repo = repository(tmp);
        byte[] first = GitPolicyFetcher.fetch(PolicySource.parse(url(repo) + "#path=policy"));
        git(repo, "commit", "-q", "--allow-empty", "-m", "no change");
        byte[] second = GitPolicyFetcher.fetch(PolicySource.parse(url(repo) + "#path=policy"));

        assertEquals(PolicySourceResolver.sha256(first), PolicySourceResolver.sha256(second));
    }

    @Test
    void aWrongPinIsRefused(@TempDir Path tmp) throws IOException {
        Path repo = repository(tmp);

        BundleValidationException e = assertThrows(BundleValidationException.class,
                () -> engine(url(repo) + "#path=policy&sha256=" + "1".repeat(64), null));
        assertTrue(e.getMessage().contains("does not match its pin"), e.getMessage());
        BundleValidationException version = assertThrows(BundleValidationException.class,
                () -> engine(url(repo) + "#path=policy&version=9.9.9", null));
        assertTrue(version.getMessage().contains("9.9.9") || version.getMessage().contains("version"), version.getMessage());
    }

    @Test
    void requiringPinsAcceptsACommitOrADigestButNotABranch(@TempDir Path tmp) throws IOException {
        Path repo = repository(tmp);
        String commit = git(repo, "rev-parse", "HEAD");

        BundleValidationException e = assertThrows(BundleValidationException.class,
                () -> Engine.builder().policySource(url(repo) + "#ref=main&path=policy").requirePin(true).cacheDir(null).build());
        assertTrue(e.getMessage().contains("commit ref"), e.getMessage());
        assertEquals("2.0.0", version(Engine.builder().policySource(url(repo) + "#ref=" + commit + "&path=policy").requirePin(true).cacheDir(null).build()));
    }

    @Test
    void unsafeRepositoriesPathsAndRefsAreRefused(@TempDir Path tmp) throws IOException {
        Path repo = repository(tmp);

        assertThrows(BundleValidationException.class, () -> engine("git:ext::sh -c touch% /tmp/x", null));
        assertThrows(BundleValidationException.class, () -> engine("git:--upload-pack=x", null));
        assertThrows(BundleValidationException.class, () -> engine(url(repo) + "#path=../outside", null));
        assertThrows(BundleValidationException.class, () -> engine(url(repo) + "#path=/etc", null));
        assertThrows(BundleValidationException.class, () -> engine(url(repo) + "#ref=--output=x&path=policy", null));
    }

    @Test
    void aMissingRefOrFolderIsAClearError(@TempDir Path tmp) throws IOException {
        Path repo = repository(tmp);

        BundleValidationException ref = assertThrows(BundleValidationException.class, () -> engine(url(repo) + "#ref=nope&path=policy", null));
        assertTrue(ref.getMessage().contains("could not be fetched"), ref.getMessage());
        BundleValidationException folder = assertThrows(BundleValidationException.class, () -> engine(url(repo) + "#path=missing", null));
        assertTrue(folder.getMessage().contains("no folder 'missing'"), folder.getMessage());
    }

    @Test
    void aBundleFromGitScoresLikeAnyOther(@TempDir Path tmp) throws IOException {
        Engine engine = engine(url(repository(tmp)) + "#path=policy", null);

        ScoringResult result = engine.score(SpecInput.builder().content(MiniBundle.SPEC).format(SpecFormat.OPENAPI3).policy("Mini").build());
        assertEquals(ScoringResult.Status.SUCCESS, result.getStatus(), result.getErrorMessage());
        assertEquals(List.of("DOC001"), result.getDiagnostics().stream().map(d -> d.getRuleId()).distinct().toList());
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }
}
