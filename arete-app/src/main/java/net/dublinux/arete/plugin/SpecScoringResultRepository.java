package net.dublinux.arete.plugin;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpecScoringResultRepository extends JpaRepository<SpecScoringResultEntity, Long> {

    void deleteBySpecId(Long specId);
}
