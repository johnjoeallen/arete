package net.dublinux.arete.repository;

import net.dublinux.arete.domain.SpecEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SpecRepository extends JpaRepository<SpecEntity, Long> {

    Optional<SpecEntity> findByFilePath(String filePath);

    Optional<SpecEntity> findByTitle(String title);

    Optional<SpecEntity> findByRef(String ref);

    Optional<SpecEntity> findByNamespaceAndTitle(String namespace, String title);

    List<SpecEntity> findByNamespaceOrderByTitleAsc(String namespace);

    List<SpecEntity> findByNamespaceAndSubmitterOrderByTitleAsc(String namespace, String submitter);

    Optional<SpecEntity> findByIdAndNamespace(Long id, String namespace);

    @Query("select distinct s.namespace from SpecEntity s order by s.namespace")
    List<String> findDistinctNamespaces();

    long countByNamespace(String namespace);
}
