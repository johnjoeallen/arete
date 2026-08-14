package com.speculate.service;

import com.speculate.repository.SpecRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.annotation.DirtiesContext;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises {@link SpecFileWatcher} against a real filesystem and a real
 * {@link SpecStorageService}/{@link SpecParserService} (only the debounce is
 * shortened) since its whole job is reacting to genuine OS file-change
 * notifications — a mocked filesystem wouldn't prove anything.
 *
 * <p>The watcher saves from background threads, outside the main test
 * thread's transaction, so {@code @DataJpaTest}'s usual per-test rollback
 * never sees those writes — they're real commits. {@code @DirtiesContext}
 * forces a fresh embedded database before every test method so one test's
 * committed rows can't leak into the next (in this class or, since Spring
 * caches contexts by configuration, any other {@code @DataJpaTest} class).
 */
@DataJpaTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SpecFileWatcherTest {

    private static final Duration AWAIT_TIMEOUT = Duration.ofSeconds(5);

    @Autowired
    private SpecRepository repository;

    private SpecStorageService storageService;
    private SpecParserService parserService;
    private SpecFileWatcher watcher;

    @TempDir
    private Path specsHome;

    @TempDir
    private Path externalDir;

    @BeforeEach
    void setUp() {
        storageService = new SpecStorageService(repository);
        parserService = new SpecParserService();
        watcher = new SpecFileWatcher(specsHome, 50, parserService, storageService);
        watcher.start();
    }

    @AfterEach
    void tearDown() {
        watcher.stop();
    }

    @Test
    void droppingASpecFileIntoTheSpecsHomeLoadsItAutomatically() throws Exception {
        Files.writeString(specsHome.resolve("dropped.yaml"), specWithTitle("Dropped API"), StandardCharsets.UTF_8);

        awaitUntil(() -> repository.findByTitle("Dropped API").isPresent());
    }

    @Test
    void editingAFileAlreadyInTheSpecsHomeUpdatesTheStoredCopy() throws Exception {
        Path file = specsHome.resolve("watched.yaml");
        Files.writeString(file, specWithTitle("Original Title"), StandardCharsets.UTF_8);
        awaitUntil(() -> repository.findByTitle("Original Title").isPresent());

        Files.writeString(file, specWithTitle("Renamed Title"), StandardCharsets.UTF_8);

        awaitUntil(() -> repository.findByTitle("Renamed Title").isPresent());
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void watchingAnExternalFilePicksUpLaterEdits() throws Exception {
        Path file = externalDir.resolve("external.yaml");
        Files.writeString(file, specWithTitle("External API"), StandardCharsets.UTF_8);

        watcher.watch(file);
        Files.writeString(file, specWithTitle("External API v2"), StandardCharsets.UTF_8);

        awaitUntil(() -> repository.findByTitle("External API v2").isPresent());
    }

    /**
     * Proves the {@code OVERFLOW} recovery path in isolation: builds a
     * watcher that's never {@code start()}ed, so no real filesystem event
     * could reach it, and calls {@link SpecFileWatcher#rescanDir} directly
     * (the same call the watch loop makes on a real {@code OVERFLOW}) as the
     * only possible cause of the reload. Windows' WatchService overflows far
     * more readily than Linux's, dropping edits it should have delivered as
     * {@code ENTRY_MODIFY} — this is the recovery for exactly that.
     */
    @Test
    void rescanDirRecoversAnEditToAFileInTheSpecsHome() throws Exception {
        watcher.stop(); // otherwise setUp's already-running watcher would also see and race to save this file
        SpecFileWatcher freshWatcher = new SpecFileWatcher(specsHome, 50, parserService, storageService);
        try {
            Path file = specsHome.resolve("watched.yaml");
            Files.writeString(file, specWithTitle("Rescanned Title"), StandardCharsets.UTF_8);

            freshWatcher.rescanDir(specsHome);

            awaitUntil(() -> repository.findByTitle("Rescanned Title").isPresent());
        } finally {
            freshWatcher.stop();
        }
    }

    @Test
    void rescanDirRecoversAnEditToAnExternallyTrackedFile() throws Exception {
        watcher.stop(); // otherwise setUp's already-running watcher would also see and race to save this file
        SpecFileWatcher freshWatcher = new SpecFileWatcher(specsHome, 50, parserService, storageService);
        try {
            Path file = externalDir.resolve("external.yaml");
            Files.writeString(file, specWithTitle("External Rescanned"), StandardCharsets.UTF_8);
            freshWatcher.watch(file);

            freshWatcher.rescanDir(externalDir);

            awaitUntil(() -> repository.findByTitle("External Rescanned").isPresent());
        } finally {
            freshWatcher.stop();
        }
    }

    @Test
    void aFileWithNoTitleIsSkippedRatherThanSaved() throws Exception {
        Files.writeString(specsHome.resolve("no-title.yaml"), "openapi: 3.0.0\ninfo: {}", StandardCharsets.UTF_8);
        // Give the watcher a chance to (not) act, then prove nothing was saved.
        Thread.sleep(200);

        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    void filesAlreadySittingInTheSpecsHomeAtStartupAreLoaded() throws Exception {
        watcher.stop();
        Files.writeString(specsHome.resolve("preexisting.yaml"), specWithTitle("Preexisting API"), StandardCharsets.UTF_8);

        SpecFileWatcher freshWatcher = new SpecFileWatcher(specsHome, 50, parserService, storageService);
        try {
            freshWatcher.start();
            awaitUntil(() -> repository.findByTitle("Preexisting API").isPresent());
        } finally {
            freshWatcher.stop();
        }
    }

    private static String specWithTitle(String title) {
        return "openapi: 3.0.0\ninfo:\n  title: " + title + "\n  version: 1.0.0\n";
    }

    private static void awaitUntil(Supplier<Boolean> condition) throws InterruptedException {
        Instant deadline = Instant.now().plus(AWAIT_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            if (condition.get()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Condition not met within " + AWAIT_TIMEOUT);
    }

}
