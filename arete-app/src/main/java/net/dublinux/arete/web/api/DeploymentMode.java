package net.dublinux.arete.web.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * {@code arete.deployment.mode} — {@code local} (default) or {@code shared}.
 *
 * <p>{@code shared} means multi-user or network-exposed: local-filesystem
 * reach becomes a cross-user disclosure risk, so {@code file:} URLs, the
 * {@code /api/load-file} endpoint and the drop-folder watcher are locked down.
 */
@Component
public class DeploymentMode {

    private final boolean shared;

    public DeploymentMode(@Value("${arete.deployment.mode:local}") String mode) {
        this.shared = "shared".equalsIgnoreCase(mode == null ? "" : mode.trim());
    }

    public boolean isShared() {
        return shared;
    }

    public boolean isLocal() {
        return !shared;
    }
}
