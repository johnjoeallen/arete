package net.dublinux.arete.engine.gate;

import java.util.ArrayList;
import java.util.List;

/** The gate's verdict over every spec it looked at. */
public record GateResult(String baseDescription, List<SpecResult> specs) {
    public GateResult {
        specs = List.copyOf(specs);
    }

    /** The specs scored although their text matches the base. */
    public List<SpecResult> unchanged() { return specs.stream().filter(SpecResult::unchanged).toList(); }

    /** True when every spec passed; true when there were none to check. */
    public boolean passed() { return specs.stream().allMatch(SpecResult::passed); }

    /** True when the engine could not score something (a configuration problem, not a verdict). */
    public boolean hasEngineError() {
        return specs.stream().anyMatch(s -> "ENGINE_ERROR".equals(s.head().status()));
    }

    public List<String> reasons() {
        List<String> reasons = new ArrayList<>();
        for (SpecResult spec : specs) {
            for (String reason : spec.reasons()) reasons.add(spec.file() + ": " + reason);
        }
        return reasons;
    }
}
