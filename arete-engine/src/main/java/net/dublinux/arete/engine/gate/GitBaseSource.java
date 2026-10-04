package net.dublinux.arete.engine.gate;

import java.util.Optional;

/** The base read with {@code git show <commit>:<path>}: no checkout, no second working tree. */
final class GitBaseSource implements BaseSource {
    private final Git git;
    private final String commit;

    GitBaseSource(Git git, String commit) {
        this.git = git;
        this.commit = commit;
    }

    @Override public Optional<String> read(String repoRelativePath) {
        Git.Result result = git.run("show", commit + ":" + repoRelativePath);
        return result.ok() ? Optional.of(result.out()) : Optional.empty();
    }

    @Override public String description() { return "git " + commit.substring(0, Math.min(10, commit.length())); }
}
