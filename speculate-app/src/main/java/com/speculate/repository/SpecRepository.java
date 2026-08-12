package com.speculate.repository;

import com.speculate.domain.SpecEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpecRepository extends JpaRepository<SpecEntity, Long> {

    Optional<SpecEntity> findByTitle(String title);

    Optional<SpecEntity> findByFilePath(String filePath);

}
