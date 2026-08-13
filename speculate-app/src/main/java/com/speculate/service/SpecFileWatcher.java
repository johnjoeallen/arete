package com.speculate.service;

import com.speculate.domain.SpecEntity;
import com.speculate.domain.SpecSource;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Watches the local filesystem for spec files and keeps their stored copies
 * in sync automatically. Two sources feed it:
 *
 * <ul>
 *     <li>The {@code specsHome} "drop folder" (e.g. {@code ~/.speculate/specs}),
 *     watched wholesale — any file dropped in is loaded, and any edit to a
 *     file already there is picked up, with no action required elsewhere.</li>
 *     <li>Individual files loaded from arbitrary paths via {@link #watch},
 *     called after a successful {@code /api/load-file} — only that specific
 *     filename is watched within its directory.</li>
 * </ul>
 *
 * {@link java.nio.file.WatchService} only watches directories (and isn't
 * recursive), so both cases register a directory and, for the second case,
 * filter events down to the filenames actually being tracked.
 */
@Component
public class SpecFileWatcher {

    private static final Logger log = LoggerFactory.getLogger(SpecFileWatcher.class);

    private final Path specsHome;
    private final long debounceMillis;
    private final SpecParserService specParserService;
    private final SpecStorageService specStorageService;

    private final WatchService watchService;
    private final Set<Path> registeredDirs = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<WatchKey, Path> watchedDirsByKey = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Path, Set<String>> trackedFilenamesByDir = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Path, ScheduledFuture<?>> pendingReloads = new ConcurrentHashMap<>();

    private final ScheduledExecutorService debouncer =
            Executors.newSingleThreadScheduledExecutor(SpecFileWatcher::newDaemonThread);
    private final ScheduledExecutorService watchLoopExecutor =
            Executors.newSingleThreadScheduledExecutor(SpecFileWatcher::newDaemonThread);

    @Autowired
    public SpecFileWatcher(
            @Value("${speculate.specs-dir}") String specsDir,
            @Value("${speculate.watch-debounce-ms:300}") long debounceMillis,
            SpecParserService specParserService,
            SpecStorageService specStorageService) {
        this(Path.of(specsDir), debounceMillis, specParserService, specStorageService);
    }

    SpecFileWatcher(Path specsHome, long debounceMillis, SpecParserService specParserService,
            SpecStorageService specStorageService) {
        this.specsHome = specsHome.toAbsolutePath().normalize();
        this.debounceMillis = debounceMillis;
        this.specParserService = specParserService;
        this.specStorageService = specStorageService;
        try {
            this.watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @PostConstruct
    void start() {
        try {
            Files.createDirectories(specsHome);
        } catch (IOException e) {
            log.warn("Could not create specs drop folder {}; drop-folder watching is disabled: {}",
                    specsHome, e.toString());
            return;
        }
        registerDir(specsHome);

        try (var files = Files.list(specsHome)) {
            files.filter(Files::isRegularFile).forEach(this::loadOrRefresh);
        } catch (IOException e) {
            log.warn("Could not scan specs drop folder {}: {}", specsHome, e.toString());
        }

        // Re-arm watches (and reconcile any offline edits) for specs that were
        // loaded from an external path in a previous run of the app.
        specStorageService.findAll().stream()
                .filter(e -> e.getSource() == SpecSource.FILE && e.getFilePath() != null)
                .map(SpecEntity::getFilePath)
                .map(Path::of)
                .filter(path -> !path.toAbsolutePath().normalize().getParent().equals(specsHome))
                .distinct()
                .forEach(path -> {
                    watch(path);
                    loadOrRefresh(path);
                });

        watchLoopExecutor.submit(this::watchLoop);
    }

    @PreDestroy
    void stop() {
        try {
            watchService.close();
        } catch (IOException ignored) {
            // closing unblocks the loop's take(); the failure itself doesn't matter on shutdown.
        }
        watchLoopExecutor.shutdownNow();
        debouncer.shutdownNow();
    }

    /** Starts watching {@code filePath} for changes; a no-op if it's already covered by the drop folder. */
    public void watch(Path filePath) {
        Path absolute = filePath.toAbsolutePath().normalize();
        Path dir = absolute.getParent();
        if (dir == null || dir.equals(specsHome)) {
            return;
        }
        trackedFilenamesByDir.computeIfAbsent(dir, d -> ConcurrentHashMap.newKeySet())
                .add(absolute.getFileName().toString());
        registerDir(dir);
    }

    public Path getSpecsHome() {
        return specsHome;
    }

    /** Immediately (re)loads a file, bypassing the debounce — for callers reacting to a non-filesystem event. */
    public void reload(Path path) {
        loadOrRefresh(path);
    }

    private void registerDir(Path dir) {
        if (!registeredDirs.add(dir)) {
            return;
        }
        try {
            WatchKey key = dir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
            watchedDirsByKey.put(key, dir);
        } catch (IOException e) {
            registeredDirs.remove(dir);
            log.warn("Could not watch directory {} for spec file changes: {}", dir, e.toString());
        }
    }

    private void watchLoop() {
        while (true) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (Exception e) {
                return; // watch service closed (normal shutdown) or thread interrupted
            }

            Path dir = watchedDirsByKey.get(key);
            if (dir != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                        continue;
                    }
                    Path changed = dir.resolve((Path) event.context());
                    boolean relevant = dir.equals(specsHome)
                            || trackedFilenamesByDir.getOrDefault(dir, Set.of())
                                    .contains(changed.getFileName().toString());
                    if (relevant) {
                        scheduleReload(changed);
                    }
                }
            }

            if (!key.reset()) {
                watchedDirsByKey.remove(key);
            }
        }
    }

    private void scheduleReload(Path path) {
        pendingReloads.compute(path, (p, existing) -> {
            if (existing != null) {
                existing.cancel(false);
            }
            return debouncer.schedule(() -> {
                pendingReloads.remove(path);
                loadOrRefresh(path);
            }, debounceMillis, TimeUnit.MILLISECONDS);
        });
    }

    private void loadOrRefresh(Path path) {
        try {
            if (!Files.isRegularFile(path)) {
                return; // deleted, or a directory event we don't care about
            }
            String content = Files.readString(path, StandardCharsets.UTF_8);
            ParsedSpec parsed = specParserService.parse(content);
            String title = parsed.title();
            if (parsed.openApi() == null || title == null) {
                log.warn("Not loading {}: spec failed to parse, or has no info.title", path);
                return;
            }
            specStorageService.saveOrUpdateFromFile(title, content, path.toString());
        } catch (Exception e) {
            log.warn("Failed to load spec from {}: {}", path, e.toString());
        }
    }

    private static Thread newDaemonThread(Runnable r) {
        Thread thread = new Thread(r, "spec-file-watcher");
        thread.setDaemon(true);
        return thread;
    }
}
