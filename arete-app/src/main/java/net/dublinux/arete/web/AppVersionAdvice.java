package net.dublinux.arete.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Makes the app version available to every view as {@code appVersion} (shown beside the logo). */
@ControllerAdvice(basePackages = "net.dublinux.arete.web")
public class AppVersionAdvice {

    private final String version;

    public AppVersionAdvice(@Value("${arete.version:}") String version) {
        // Unresolved placeholder / snapshot in a dev run → show nothing rather than "@project.version@".
        this.version = version == null || version.isBlank() || version.startsWith("@") ? "" : version;
    }

    @ModelAttribute("appVersion")
    public String appVersion() {
        return version;
    }
}
