package net.dublinux.arete.scoring;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpecEngineSettingsRepository extends JpaRepository<SpecEngineSettingsEntity, SpecEngineSettingsId> {

    Optional<SpecEngineSettingsEntity> findBySpecIdAndEngineId(Long specId, String engineId);

    void deleteBySpecId(Long specId);
}
