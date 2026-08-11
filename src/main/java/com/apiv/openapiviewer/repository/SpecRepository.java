package com.apiv.openapiviewer.repository;

import com.apiv.openapiviewer.domain.SpecEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpecRepository extends JpaRepository<SpecEntity, Long> {

    Optional<SpecEntity> findByTitle(String title);

}
