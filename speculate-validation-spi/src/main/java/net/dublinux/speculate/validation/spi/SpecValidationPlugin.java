package net.dublinux.speculate.validation.spi;

import java.util.Map;
import java.util.Set;

/**
 * A linter engine adapter, discovered dynamically via {@link
 * java.util.ServiceLoader} from a jar dropped in the host's {@code
 * plugins/} folder. Implementations declare themselves via {@code
 * META-INF/services/net.dublinux.speculate.validation.spi.SpecValidationPlugin}
 * — no host-specific base class, annotation, or reflection wiring beyond
 * that is required (non-functional requirement re: no reflection tricks).
 *
 * <h2>Classloading contract</h2>
 * Every type that appears in this interface's method signatures — return
 * types, parameter types, and everything reachable from them — must be
 * either a JDK type or declared in this module, per constraint #4. This
 * module is loaded once by the host and used as the shared parent
 * classloader for every plugin's isolated {@code URLClassLoader}, so these
 * are the only types safe to pass across that boundary (constraint #5).
 * Do not add a method whose signature mentions e.g. a swagger-parser type
 * or a Kotlin type — that would defeat the whole isolation scheme.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>The host instantiates the plugin (no-arg constructor, via {@code
 *       ServiceLoader}).</li>
 *   <li>{@link #configure(Map)} is called exactly once, before any call to
 *       {@link #validate(SpecInput)}.</li>
 *   <li>{@link #validate(SpecInput)} may then be called any number of
 *       times, potentially concurrently — see thread-safety note below.</li>
 * </ol>
 *
 * <h2>Thread safety &amp; statelessness</h2>
 * The host's {@code PluginLoader} loads one instance per plugin jar and
 * reuses it across calls, so implementations must be safe for concurrent
 * {@link #validate(SpecInput)} invocations after {@link #configure(Map)}
 * has completed. In practice this means: don't mutate instance fields
 * inside {@code validate()}, and if the wrapped engine has its own
 * shared/managed state (e.g. a Spring-managed rule engine in some
 * adapter), either confirm that engine is safe for concurrent use or
 * synchronize internally in the adapter — the interface itself has no way
 * to express or enforce this, so it is an implementation obligation of
 * each adapter.
 *
 * <h2>Failure handling</h2>
 * {@code validate()} does not declare any checked exceptions — see {@link
 * ValidationResult} for why. Implementations should catch their own
 * unexpected failures and return {@link ValidationResult#pluginError}
 * rather than let an exception propagate. That said, the host must still
 * treat every call to a dynamically loaded plugin as untrusted: wrap the
 * call in a {@code catch (Throwable)} on the host side as a defensive
 * backstop (e.g. against {@code LinkageError} from a classpath mismatch),
 * independent of whatever this interface declares.
 */
public interface SpecValidationPlugin {

    /**
     * Interface contract version, for the belt-and-suspenders check
     * described in {@link #getInterfaceVersion()}. Bump only on a
     * source-incompatible change to this interface module.
     */
    int INTERFACE_VERSION = 1;

    /** Stable unique identifier for this plugin, e.g. {@code "zally"}. */
    String getId();

    /** Human-readable display name, e.g. {@code "Example API Linter"}. */
    String getName();

    /**
     * Plugin/engine version string for diagnostics and logging, e.g.
     * {@code "zally-core 2.3.1"}. Distinct from {@link #getInterfaceVersion()}.
     */
    String getVersion();

    /** Which spec dialects this plugin can validate. Never null or empty. */
    Set<SpecFormat> getSupportedFormats();

    /**
     * Open question #6: a default method rather than a required one, so
     * existing plugin jars compiled against interface v1 keep working
     * (they inherit the default) even if this method is added after the
     * fact. A plugin built against a later interface version can override
     * it to report that version; the host can then log a warning when a
     * plugin reports a version newer than the host's own {@link
     * #INTERFACE_VERSION}, as an early diagnostic on top of whatever
     * {@code ServiceLoader}/{@code LinkageError} failures a genuine binary
     * incompatibility would already surface.
     */
    default int getInterfaceVersion() {
        return INTERFACE_VERSION;
    }

    /**
     * Called exactly once after instantiation, before any {@link
     * #validate(SpecInput)} call.
     *
     * <p>Open question #4: config is a flat {@code Map<String,String>}
     * rather than plugin-specific typed config, kept deliberately generic
     * — e.g. an adapter might read a {@code "rulesetPath"} key. Typed
     * config was rejected because a typed config object is exactly the
     * kind of engine-specific type constraint #4 forbids across the
     * classloader boundary; a plugin that needs structured config can
     * parse a JSON/YAML string value out of this map itself.
     *
     * @param config never null; may be empty if the host has no
     *               plugin-specific configuration for this plugin
     */
    void configure(Map<String, String> config);

    /**
     * Validates a single spec document and returns its outcome.
     * Implementations must be stateless with respect to this call (see
     * class-level thread-safety note) and must not throw for expected
     * failure modes — see {@link ValidationResult}.
     *
     * @param input never null
     * @return never null
     */
    ValidationResult validate(SpecInput input);
}
