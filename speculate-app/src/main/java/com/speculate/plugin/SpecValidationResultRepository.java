package com.speculate.plugin;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SpecValidationResultRepository extends JpaRepository<SpecValidationResultEntity, Long> {

    void deleteBySpecId(Long specId);
}
