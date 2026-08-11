package com.apiv.openapiviewer.service;

import com.apiv.openapiviewer.domain.SpecEntity;
import com.apiv.openapiviewer.repository.SpecRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class SpecStorageService {

    private final SpecRepository repository;

    public SpecStorageService(SpecRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public SpecEntity saveOrReplace(String title, String rawContent) {
        SpecEntity entity = repository.findByTitle(title).orElseGet(SpecEntity::new);
        entity.setTitle(title);
        entity.setRawContent(rawContent);
        entity.setUpdatedAt(Instant.now());
        return repository.save(entity);
    }

    public List<SpecEntity> findAll() {
        return repository.findAll();
    }

    public Optional<SpecEntity> findById(Long id) {
        return repository.findById(id);
    }

    @Transactional
    public void deleteById(Long id) {
        repository.deleteById(id);
    }

}
