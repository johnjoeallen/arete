package net.dublinux.arete.engine.gate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** The few git commands the gate needs, run through the {@code git} program. */
final class Git {
    record Result(int exitCode, String out, String err) {
        boolean ok() { return exitCode == 0; }
    }

    private final Path directory;

    Git(Path directory) { this.directory = directory; }

    Result run(String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory.toFile());
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        builder.environment().put("LC_ALL", "C");
        try {
            Process process = builder.start();
            // Read both streams while it runs, so a large diff cannot fill a pipe and stall it.
            var out = new java.util.concurrent.CompletableFuture<String>();
            var err = new java.util.concurrent.CompletableFuture<String>();
            startDaemon(() -> out.complete(read(process.getInputStream())));
            startDaemon(() -> err.complete(read(process.getErrorStream())));
            if (!process.waitFor(120, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new GateException("git " + String.join(" ", args) + " did not finish in two minutes");
            }
            return new Result(process.exitValue(), out.get(5, TimeUnit.SECONDS), err.get(5, TimeUnit.SECONDS));
        } catch (IOException e) {
            throw new GateException("could not run git (" + e.getMessage() + "); the gate's git mode needs the git program");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GateException("interrupted running git");
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            throw new GateException("could not read git's output: " + e.getMessage());
        }
    }

    private static void startDaemon(Runnable task) {
        Thread thread = new Thread(task, "git-output");
        thread.setDaemon(true);
        thread.start();
    }

    private static String read(java.io.InputStream in) {
        try {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
