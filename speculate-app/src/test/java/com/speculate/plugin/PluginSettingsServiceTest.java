package com.speculate.plugin;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PluginSettingsServiceTest {

    @Autowired
    private PluginSettingsRepository repository;

    private PluginSettingsService service;

    @BeforeEach
    void setUp() {
        service = new PluginSettingsService(repository);
    }

    @Test
    void newPluginIdDefaultsToEnabled() {
        assertThat(service.isEnabled("never-seen-before")).isTrue();
    }

    @Test
    void ensureDefaultsOnlyInsertsMissingIds() {
        service.setEnabled("existing", false);

        service.ensureDefaults(List.of("existing", "brand-new"));

        assertThat(service.isEnabled("existing")).isFalse();
        assertThat(service.isEnabled("brand-new")).isTrue();
        assertThat(repository.findById("existing")).isPresent();
        assertThat(repository.findById("brand-new")).isPresent();
    }

    @Test
    void setEnabledPersistsAcrossLookups() {
        service.setEnabled("toggle-me", true);
        service.setEnabled("toggle-me", false);

        assertThat(service.isEnabled("toggle-me")).isFalse();
    }

}
