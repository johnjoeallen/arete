package net.dublinux.arete.engine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Reads a policy bundle from a git repository with the {@code git} program, so it uses whatever credentials the
 * machine already has (ssh keys, a credential helper). The wanted ref is fetched shallowly into a scratch
 * repository, the bundle folder is read from it and packed into a zip, and the scratch repository is deleted.
 *
 * <p>The zip is written to be the same for the same files: entries sorted by name, stored not compressed, with a
 * fixed timestamp. That makes its SHA-256 a stable pin, and lets the resolver cache and verify it like any archive.
 */
final class GitPolicyFetcher {
    private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}|[0-9a-f]{64}");
    private static final Pattern SCP_LIKE = Pattern.compile("[A-Za-z0-9._-]+@[A-Za-z0-9.-]+:[A-Za-z0-9._~/+-]+");
    private static final LocalDateTime EPOCH = LocalDateTime.of(1980, 1, 1, 0, 0);

    private GitPolicyFetcher() { }

    /** True when the ref is a full commit id, which names one set of files for ever. */
    static boolean pinnedByCommit(PolicySource source) {
        return source.ref() != null && COMMIT.matcher(source.ref()).matches();
    }

    static byte[] fetch(PolicySource source) {
        String repository = source.uri().substring("git:".length());
        checkRepository(repository);
        String ref = source.ref();
        if (ref != null && (ref.startsWith("-") || ref.contains("..") || ref.chars().anyMatch(c -> c <= ' ' || c == '~' || c == '^' || c == ':' || c == '\\'))) {
            throw new BundleValidationException("policy source " + source.uri() + " has an unusable ref '" + ref + "'");
        }
        String folder = source.path() == null ? "" : source.path().replace('\\', '/');
        if (folder.startsWith("/") || folder.equals("..") || folder.startsWith("../") || folder.contains("/../") || folder.endsWith("/..")) {
            throw new BundleValidationException("policy source " + source.uri() + " has a path outside the repository: " + folder);
        }
        Path scratch = null;
        try {
            scratch = Files.createTempDirectory("arete-policy-git");
            run(scratch, source, "init", "-q");
            Result fetched = run(scratch, source, "fetch", "-q", "--depth", "1", "--", repository, ref == null ? "HEAD" : ref);
            if (!fetched.ok() && ref != null && COMMIT.matcher(ref).matches()) {
                // Some servers will not serve a bare commit id shallowly; take the branches and look for it.
                fetched = run(scratch, source, "fetch", "-q", "--", repository);
                if (fetched.ok() && !run(scratch, source, "cat-file", "-e", ref + "^{commit}").ok()) {
                    throw new BundleValidationException("policy source " + source.uri() + ": commit " + ref + " is not in the repository's branches");
                }
            } else if (!fetched.ok()) {
                throw new BundleValidationException("policy source " + source.uri() + " could not be fetched: " + fetched.err().strip());
            }
            String target = ref != null && COMMIT.matcher(ref).matches() ? ref : "FETCH_HEAD";
            Result checkout = run(scratch, source, "checkout", "-q", "--detach", target);
            if (!checkout.ok()) throw new BundleValidationException("policy source " + source.uri() + " could not be checked out: " + checkout.err().strip());
            Path root = folder.isEmpty() ? scratch : scratch.resolve(folder).normalize();
            if (!root.startsWith(scratch) || !Files.isDirectory(root)) {
                throw new BundleValidationException("policy source " + source.uri() + " has no folder '" + folder + "' at " + (ref == null ? "the default branch" : ref));
            }
            return pack(root, scratch.resolve(".git"), source);
        } catch (IOException e) {
            throw new BundleValidationException("policy source " + source.uri() + " could not be read: " + e.getMessage());
        } finally {
            if (scratch != null) delete(scratch);
        }
    }

    private static void checkRepository(String repository) {
        boolean allowed = repository.startsWith("https://") || repository.startsWith("ssh://") || repository.startsWith("file://")
                || SCP_LIKE.matcher(repository).matches();
        if (!allowed || repository.startsWith("-")) {
            throw new BundleValidationException("git policy source '" + repository + "' must be an https://, ssh:// or file:// URL, or user@host:path");
        }
    }

    private static byte[] pack(Path root, Path gitDir, PolicySource source) throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : (Iterable<Path>) walk::iterator) {
                if (path.startsWith(gitDir) || path.getFileName() != null && path.getFileName().toString().equals(".git")) continue;
                if (Files.isSymbolicLink(path)) throw new BundleValidationException("policy source " + source.uri() + " contains a symbolic link: " + root.relativize(path));
                if (Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) files.add(path);
            }
        }
        files.sort(Comparator.comparing(path -> root.relativize(path).toString().replace('\\', '/')));
        if (files.size() > ZipBundleResources.MAX_ENTRIES) throw new BundleValidationException("policy source " + source.uri() + " has too many files");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        long total = 0;
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.setMethod(ZipOutputStream.STORED);
            for (Path file : files) {
                long size = Files.size(file);
                total += size;
                if (size > ZipBundleResources.MAX_ENTRY_BYTES || total > ZipBundleResources.MAX_TOTAL_BYTES) {
                    throw new BundleValidationException("policy source " + source.uri() + " is too large to be a policy bundle");
                }
                byte[] content = Files.readAllBytes(file);
                CRC32 crc = new CRC32();
                crc.update(content);
                ZipEntry entry = new ZipEntry(root.relativize(file).toString().replace('\\', '/'));
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(content.length);
                entry.setCompressedSize(content.length);
                entry.setCrc(crc.getValue());
                entry.setTimeLocal(EPOCH);
                zip.putNextEntry(entry);
                zip.write(content);
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    // ---- running git ----------------------------------------------------------------------------------------

    private record Result(int exitCode, String err) {
        boolean ok() { return exitCode == 0; }
    }

    private static Result run(Path directory, PolicySource source, String... args) {
        List<String> command = new ArrayList<>(List.of("git", "-c", "protocol.ext.allow=never", "-c", "core.hooksPath=/dev/null"));
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile());
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        builder.environment().put("LC_ALL", "C");
        try {
            Process process = builder.start();
            CompletableFuture<String> err = new CompletableFuture<>();
            daemon(() -> err.complete(read(process.getErrorStream())));
            daemon(() -> read(process.getInputStream()));
            if (!process.waitFor(120, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new BundleValidationException("policy source " + source.uri() + ": git " + args[0] + " did not finish in two minutes");
            }
            return new Result(process.exitValue(), err.get(5, TimeUnit.SECONDS));
        } catch (IOException e) {
            throw new BundleValidationException("policy source " + source.uri() + " needs the git program (" + e.getMessage() + ")");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BundleValidationException("policy source " + source.uri() + ": interrupted running git");
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new BundleValidationException("policy source " + source.uri() + ": could not read git's output");
        }
    }

    private static void daemon(Runnable task) {
        Thread thread = new Thread(task, "git-policy-output");
        thread.setDaemon(true);
        thread.start();
    }

    private static String read(InputStream in) {
        try {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static void delete(Path directory) {
        try (Stream<Path> walk = Files.walk(directory)) {
            walk.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        } catch (IOException ignored) {
            // A scratch directory left behind in the temp folder is harmless.
        }
    }
}
