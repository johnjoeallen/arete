package net.dublinux.arete.service;

import net.dublinux.arete.domain.NamespaceEntity;
import net.dublinux.arete.domain.SpecEntity;
import net.dublinux.arete.domain.SpecSource;
import net.dublinux.arete.repository.NamespaceRepository;
import net.dublinux.arete.repository.SpecRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class NamespaceServiceTest {

    @Autowired NamespaceRepository namespaceRepository;
    @Autowired SpecRepository specRepository;

    private NamespaceService service;

    @BeforeEach
    void setUp() {
        service = new NamespaceService(namespaceRepository, specRepository);
    }

    @Test
    void keepsTheTypedCasingButKeysOnLowercase() {
        NamespaceEntity a = service.resolveOrCreate("Payments Team");
        NamespaceEntity b = service.resolveOrCreate("payments-team");

        assertThat(a.getName()).isEqualTo("Payments Team");
        assertThat(a.getNameKey()).isEqualTo("payments-team");
        assertThat(b.getId()).isEqualTo(a.getId());          // same namespace
        assertThat(namespaceRepository.count()).isEqualTo(1);
    }

    @Test
    void blankOrUnusableResolvesToDefault() {
        assertThat(service.resolveOrCreate("  ").getNameKey()).isEqualTo("default");
        assertThat(service.resolveOrCreate("!!!").getNameKey()).isEqualTo("default");
    }

    @Test
    void createRejectsADuplicateKey() {
        assertThat(service.create("Mobile CI")).isTrue();
        assertThat(service.create("mobile ci")).isFalse();
    }

    @Test
    void deleteOnlyWhenEmptyAndNotDefault() {
        service.create("Scratch");
        assertThat(service.deleteIfEmpty("scratch")).isTrue();

        service.resolveOrCreate("Held");
        specRepository.save(spec("held", "An API"));
        assertThat(service.deleteIfEmpty("held")).isFalse();

        service.ensureDefault();
        assertThat(service.deleteIfEmpty("default")).isFalse();
    }

    @Test
    void listBackfillsRowsForKeysSeenOnlyInSpecs() {
        specRepository.save(spec("legacy", "Legacy API"));
        var list = service.list();
        assertThat(list).extracting(NamespaceService.Namespace::key).contains("default", "legacy");
        assertThat(list).filteredOn(n -> n.key().equals("legacy")).singleElement()
                .extracting(NamespaceService.Namespace::specCount).isEqualTo(1L);
    }

    private static SpecEntity spec(String namespaceKey, String title) {
        SpecEntity e = new SpecEntity();
        e.setNamespace(namespaceKey);
        e.setSubmitter("t");
        e.setTitle(title);
        e.setRawContent("openapi: 3.0.0");
        e.setSource(SpecSource.PASTED);
        e.setUpdatedAt(Instant.now());
        return e;
    }
}
