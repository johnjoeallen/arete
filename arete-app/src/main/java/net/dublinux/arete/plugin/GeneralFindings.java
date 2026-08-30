package net.dublinux.arete.plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Findings whose JSON Pointer location doesn't fall under a specific
 * endpoint ({@link EndpointFindings}) or a Model-tab {@code components}
 * entry ({@link ComponentFindings}) — e.g. against {@code info}, {@code
 * servers}, {@code security}, a {@code components} category the Model tab
 * doesn't render (parameters, security schemes...), or a path with no
 * specific method. Surfaced separately so the visible finding counts
 * always reconcile with a plugin's total diagnostic count instead of
 * silently dropping anything that isn't attributable to an endpoint or a
 * Model-tab entry.
 */
public final class GeneralFindings {

    private GeneralFindings() {
    }

    public static EndpointFindingsView unattributed(List<AttributedDiagnostic> diagnostics) {
        List<AttributedDiagnostic> unattributed = new ArrayList<>();
        nextDiagnostic:
        for (AttributedDiagnostic av : diagnostics) {
            String pointer = av.diagnostic().getPointer();
            if (EndpointFindings.endpointKey(pointer) != null) {
                continue;
            }
            for (String category : ComponentFindings.CATEGORIES) {
                if (ComponentFindings.componentKey(category, pointer) != null) {
                    continue nextDiagnostic;
                }
            }
            unattributed.add(av);
        }
        return new EndpointFindingsView(SeverityCounts.from(unattributed), unattributed);
    }
}
