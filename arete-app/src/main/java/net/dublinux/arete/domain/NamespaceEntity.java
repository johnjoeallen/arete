package net.dublinux.arete.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A namespace: an organisational label for grouping specs. Not a security
 * boundary. {@link #name} keeps the casing the creator typed; {@link #nameKey}
 * is the lower-cased form used for uniqueness and lookups, so {@code Payments}
 * and {@code payments} are the same namespace.
 */
@Entity
@Table(name = "namespaces")
public class NamespaceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "name_key", nullable = false, unique = true, length = 100)
    private String nameKey;

    @Column(nullable = false)
    private Instant createdAt;

    protected NamespaceEntity() {
    }

    public NamespaceEntity(String name, String nameKey) {
        this.name = name;
        this.nameKey = nameKey;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNameKey() {
        return nameKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
