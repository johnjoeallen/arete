package speculate.validation.noop;

import speculate.validation.spi.SpecFormat;
import speculate.validation.spi.SpecInput;
import speculate.validation.spi.SpecValidationPlugin;
import speculate.validation.spi.ValidationResult;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Minimal reference implementation of {@link SpecValidationPlugin}. Does not
 * parse or inspect the input at all; it exists to prove the discovery/loading
 * pipeline (jar in {@code plugins/} -&gt; isolated {@code URLClassLoader} -&gt;
 * {@code ServiceLoader} -&gt; instance -&gt; {@code validate()} -&gt; result shown in
 * the UI) works before any real linter logic exists, and to serve as the
 * smallest possible example for anyone writing a future plugin.
 */
public final class NoopValidationPlugin implements SpecValidationPlugin {

    @Override
    public String getId() {
        return "noop";
    }

    @Override
    public String getName() {
        return "No-Op Validator (always passes)";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public Set<SpecFormat> getSupportedFormats() {
        return EnumSet.allOf(SpecFormat.class);
    }

    @Override
    public void configure(Map<String, String> config) {
        // Nothing to configure.
    }

    @Override
    public ValidationResult validate(SpecInput input) {
        return ValidationResult.success(List.of(), 0);
    }
}
