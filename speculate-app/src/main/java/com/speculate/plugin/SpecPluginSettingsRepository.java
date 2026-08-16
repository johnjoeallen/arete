package com.speculate.plugin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpecPluginSettingsRepository extends JpaRepository<SpecPluginSettingsEntity, SpecPluginSettingsId> {

    Optional<SpecPluginSettingsEntity> findBySpecIdAndPluginId(Long specId, String pluginId);

    void deleteBySpecId(Long specId);
}
