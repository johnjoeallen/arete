package net.dublinux.arete.web.api;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RemoteSpecFetcherTest {

    private RemoteSpecFetcher fetcher(boolean shared, boolean allowPrivate) {
        DeploymentMode mode = new DeploymentMode(shared ? "shared" : "local");
        return new RemoteSpecFetcher(mode, allowPrivate, Duration.ofSeconds(2), DataSize.ofMegabytes(1));
    }

    @Test
    void rejectsNonHttpSchemes() {
        assertThatThrownBy(() -> fetcher(false, false).fetch("file:///etc/passwd"))
                .isInstanceOf(RemoteSpecFetcher.FetchException.class)
                .hasMessageContaining("file URLs are not allowed");
        assertThatThrownBy(() -> fetcher(false, false).fetch("ftp://example.com/spec.yaml"))
                .hasMessageContaining("only http and https");
    }

    @Test
    void blocksLoopbackAndLinkLocalByDefault() {
        assertThatThrownBy(() -> fetcher(false, false).fetch("http://127.0.0.1/spec.yaml"))
                .hasMessageContaining("blocked");
        assertThatThrownBy(() -> fetcher(false, false).fetch("http://169.254.169.254/latest/meta-data"))
                .hasMessageContaining("blocked");
        assertThatThrownBy(() -> fetcher(false, false).fetch("http://localhost:8080/x"))
                .hasMessageContaining("blocked");
    }

    @Test
    void allowPrivateIsHonouredInLocalModeButNotShared() {
        // local + allow-private: the guard is off, so we get past it to a real
        // connection attempt (which fails, but not with a "blocked" message).
        assertThatThrownBy(() -> fetcher(false, true).fetch("http://127.0.0.1:1/spec.yaml"))
                .isInstanceOf(RemoteSpecFetcher.FetchException.class)
                .hasMessageNotContaining("blocked");

        // shared: allow-private is ignored, the guard stays on.
        assertThatThrownBy(() -> fetcher(true, true).fetch("http://127.0.0.1/spec.yaml"))
                .hasMessageContaining("blocked");
    }

    @Test
    void rejectsUrlsWithNoHost() {
        assertThat(assertThatThrownBy(() -> fetcher(false, false).fetch("http:///spec.yaml"))
                .isInstanceOf(RemoteSpecFetcher.FetchException.class));
    }
}
