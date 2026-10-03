package net.dublinux.arete;

import net.dublinux.arete.engine.Engine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/** The one scoring engine the application uses, configured once at startup. */
@Configuration
public class EngineConfig {
    @Bean
    public Engine engine() {
        Engine engine = new Engine();
        engine.configure(Map.of());
        return engine;
    }
}
