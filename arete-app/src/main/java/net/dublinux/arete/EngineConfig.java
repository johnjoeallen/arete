package net.dublinux.arete;

import net.dublinux.arete.engine.Engine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/** The one scoring engine the application uses, configured once at startup. */
@Configuration
public class EngineConfig {
    /**
     * @param publicUrl where this application is reached, so a finding's "Learn more" link and a SARIF report's help URI
     *                  are absolute; set {@code arete.public-url} when it is served somewhere other than the local port
     */
    @Bean
    public Engine engine(@Value("${arete.public-url:http://localhost:${server.port:6809}}") String publicUrl) {
        Engine engine = new Engine();
        engine.configure(Map.of());
        engine.setDocumentationBaseUrl(publicUrl.replaceAll("/+$", "") + "/engines/" + Engine.ID + "/rules/");
        return engine;
    }
}
