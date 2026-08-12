package com.speculate.service;

import com.speculate.domain.SpecEntity;
import com.speculate.domain.SpecSource;
import com.speculate.repository.SpecRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SpecStorageServiceTest {

    @Autowired
    private SpecRepository repository;

    private SpecStorageService service;

    @BeforeEach
    void setUp() {
        service = new SpecStorageService(repository);
    }

    @Test
    void pastedSpecsAreRecordedAsPastedWithNoFileInfo() {
        SpecEntity saved = service.saveOrReplace("Pasted API", "openapi: 3.0.0");

        assertThat(saved.getSource()).isEqualTo(SpecSource.PASTED);
        assertThat(saved.getFilePath()).isNull();
        assertThat(saved.getContentHash()).isNull();
    }

    @Test
    void fileSpecsRecordPathAndAContentHash() {
        SpecEntity saved = service.saveOrReplaceFromFile("File API", "openapi: 3.0.0", "/home/user/spec.yaml");

        assertThat(saved.getSource()).isEqualTo(SpecSource.FILE);
        assertThat(saved.getFilePath()).isEqualTo("/home/user/spec.yaml");
        assertThat(saved.getContentHash()).isNotBlank();
    }

    @Test
    void reUploadingTheSameFileOverwritesRatherThanDuplicating() {
        service.saveOrReplaceFromFile("File API", "openapi: 3.0.0\ninfo: {}", "/home/user/spec.yaml");
        SpecEntity second = service.saveOrReplaceFromFile("File API", "openapi: 3.0.1\ninfo: {}", "/home/user/spec.yaml");

        assertThat(repository.findAll()).hasSize(1);
        assertThat(second.getRawContent()).contains("3.0.1");
    }

    @Test
    void reHashingChangedContentProducesADifferentHash() {
        // Captured immediately: saveOrReplaceFromFile returns the same managed JPA
        // entity on both calls (same title == same row), so holding a reference
        // across the second call would observe its mutated, not original, state.
        String firstHash = service.saveOrReplaceFromFile("File API", "content-v1", "/home/user/spec.yaml").getContentHash();
        String secondHash = service.saveOrReplaceFromFile("File API", "content-v2", "/home/user/spec.yaml").getContentHash();

        assertThat(secondHash).isNotEqualTo(firstHash);
    }

    @Test
    void pastingOverAnExistingFileSourcedSpecClearsItsFileAssociation() {
        service.saveOrReplaceFromFile("Same Title", "from file", "/home/user/spec.yaml");

        SpecEntity repasted = service.saveOrReplace("Same Title", "from paste");

        assertThat(repasted.getSource()).isEqualTo(SpecSource.PASTED);
        assertThat(repasted.getFilePath()).isNull();
        assertThat(repasted.getContentHash()).isNull();
    }

    @Test
    void watcherUpsertFallsBackToTitleKeyedSaveTheFirstTimeAPathIsSeen() {
        SpecEntity saved = service.saveOrUpdateFromFile("Watched API", "openapi: 3.0.0", "/home/user/watched.yaml");

        assertThat(saved.getSource()).isEqualTo(SpecSource.FILE);
        assertThat(saved.getFilePath()).isEqualTo("/home/user/watched.yaml");
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void watcherUpsertUpdatesTheSameRowInPlaceEvenWhenTheTitleChanges() {
        SpecEntity first = service.saveOrUpdateFromFile("Old Title", "openapi: 3.0.0\ninfo: {}", "/home/user/watched.yaml");
        Long id = first.getId();

        SpecEntity second = service.saveOrUpdateFromFile("New Title", "openapi: 3.0.1\ninfo: {}", "/home/user/watched.yaml");

        assertThat(second.getId()).isEqualTo(id);
        assertThat(second.getTitle()).isEqualTo("New Title");
        assertThat(repository.findAll()).hasSize(1);
    }

    @Test
    void watcherUpsertIsANoOpWhenTheContentHashHasNotChanged() {
        SpecEntity first = service.saveOrUpdateFromFile("Watched API", "openapi: 3.0.0", "/home/user/watched.yaml");
        Instant firstUpdatedAt = first.getUpdatedAt();

        SpecEntity second = service.saveOrUpdateFromFile("Watched API", "openapi: 3.0.0", "/home/user/watched.yaml");

        assertThat(second.getUpdatedAt()).isEqualTo(firstUpdatedAt);
    }

}
