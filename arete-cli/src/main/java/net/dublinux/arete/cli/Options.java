package net.dublinux.arete.cli;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Command-line arguments split into positional arguments and {@code --flag [value]} options. */
final class Options {
    /** Options that take a value; every other {@code --flag} is a switch. */
    private static final Set<String> VALUED = Set.of("--policy", "--policy-source", "--maven-repository", "--maven-settings",
            "--maven-profile", "--cache-dir", "--user-policies", "--overrides", "--format", "--out", "--fail-under");

    final List<String> positional = new ArrayList<>();
    private final Map<String, List<String>> values = new LinkedHashMap<>();
    private final Set<String> switches = new java.util.LinkedHashSet<>();

    /** Splits {@code args}; a malformed option is a {@link UsageException}. */
    static Options parse(List<String> args) {
        Options options = new Options();
        for (int i = 0; i < args.size(); i++) {
            String arg = args.get(i);
            if (!arg.startsWith("--") || arg.equals("--")) {
                options.positional.add(arg);
                continue;
            }
            String name = arg;
            String inline = null;
            int eq = arg.indexOf('=');
            if (eq > 0) {
                name = arg.substring(0, eq);
                inline = arg.substring(eq + 1);
            }
            if (VALUED.contains(name)) {
                String value = inline;
                if (value == null) {
                    if (i + 1 >= args.size()) throw new UsageException(name + " needs a value");
                    value = args.get(++i);
                }
                options.values.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
            } else if (Set.of("--require-pin", "--no-cache", "--no-overrides", "--fail-on-regression", "--help", "--version").contains(name)) {
                if (inline != null) throw new UsageException(name + " takes no value");
                options.switches.add(name);
            } else {
                throw new UsageException("unknown option " + name);
            }
        }
        return options;
    }

    boolean has(String name) { return switches.contains(name) || values.containsKey(name); }

    /** The last value given for {@code name}, or null. */
    String value(String name) {
        List<String> all = values.get(name);
        return all == null ? null : all.get(all.size() - 1);
    }

    List<String> all(String name) { return values.getOrDefault(name, List.of()); }
}
