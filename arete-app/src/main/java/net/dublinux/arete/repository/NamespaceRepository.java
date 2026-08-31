package net.dublinux.arete.repository;

import net.dublinux.arete.domain.NamespaceEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NamespaceRepository extends JpaRepository<NamespaceEntity, Long> {

    Optional<NamespaceEntity> findByNameKey(String nameKey);

    List<NamespaceEntity> findAllByOrderByNameAsc();

    boolean existsByNameKey(String nameKey);
}
