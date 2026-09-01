package net.dublinux.arete.scoring.spi;

/**
 * Generic 4-level severity scale that every plugin maps its own
 * engine-specific levels onto.
 *
 * <p>Rationale (open question #2): a fixed enum was chosen over an open
 * string. An open string ("MUST", "SHOULD", "must-fix", ...) pushes the
 * burden of interpretation onto the host — it would have to special-case
 * every engine's vocabulary to do anything useful (sort, filter, badge
 * color, "fail the build on ERROR"). A small closed enum forces each
 * plugin to do that mapping once, at the adapter boundary, where the
 * plugin author already has full knowledge of the source engine's scale.
 * Four levels was chosen because it comfortably covers the schemes we
 * know about today:
 *
 * <ul>
 *   <li>A MUST/SHOULD/MAY/HINT-style engine: MUST -&gt; ERROR, SHOULD -&gt; WARNING, MAY -&gt; INFO, HINT -&gt; HINT</li>
 *   <li>Typical linters: ERROR / WARNING / INFO map 1:1</li>
 * </ul>
 *
 * <p>If a future engine genuinely needs finer granularity, prefer adding
 * it here (a coordinated interface-module release) over reintroducing an
 * open string — the whole point is a scale every plugin and the host
 * agree on.
 */
public enum Severity {
    ERROR,
    WARNING,
    INFO,
    HINT
}
