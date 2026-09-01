#!/usr/bin/env python3
"""Write the example user policies into ~/.arete/policies/.

The Areté Policy Engine merges every ``*.md`` file under ``~/.arete/policies/``
into its bundled policy set (see docs/validation/policy-engine.md#user-policies).
This helper drops two ready-made profiles there, derived from the bundled
Enterprise Grade rule list so every rule id and parameter block stays valid:

  Lenient   — every rule enabled, 0.1 point each, nothing prohibited
  Pedantic  — every rule enabled, 2.0 points each, security rules PROHIBITED

Usage:  python3 scripts/gen-example-policies.py [--dir DIR]

Requires nothing beyond the standard library.
"""

import argparse
import pathlib
import re

REPO = pathlib.Path(__file__).resolve().parents[1]
SOURCE = REPO / "arete-policy-plugin/src/main/resources/api-policy/policies/EnterpriseGrade.md"
PROHIBIT = {"SECURITY001", "SECURITY002", "SEC009"}


def parse_rules(front_matter: str):
    """Yield (rule_id, param_lines) from the ``rules:`` block. param_lines is a
    list of raw ``      key: value`` lines, or None for a bare deduction."""
    lines = front_matter.splitlines()
    out = []
    i = lines.index("rules:") + 1
    while i < len(lines):
        bare = re.match(r"  (\w+): (.+)", lines[i])
        nested = re.match(r"  (\w+):$", lines[i])
        if bare:
            out.append((bare.group(1), None))
            i += 1
        elif nested:
            i += 1
            block = []
            while i < len(lines) and lines[i].startswith("    "):
                block.append(lines[i])
                i += 1
            out.append((nested.group(1), [b for b in block if b.startswith("      ")]))
        else:
            break
    return out


def render(policy_id: str, points: str, prohibit: bool, title: str, body: str, rules) -> str:
    lines = ["---", f"id: {policy_id}", "rules:"]
    for rid, params in rules:
        disp = "PROHIBITED" if (prohibit and rid in PROHIBIT) else points
        if params is None:
            lines.append(f"  {rid}: {disp}")
        else:
            lines += [f"  {rid}:", f"    points: {disp}", "    parameters:", *params]
    lines += ["---", "", f"# {title}", "", body, ""]
    return "\n".join(lines)


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", type=pathlib.Path,
                    default=pathlib.Path.home() / ".arete" / "policies")
    args = ap.parse_args()
    args.dir.mkdir(parents=True, exist_ok=True)

    front_matter = re.match(r"^---\n(.*?)\n---\n", SOURCE.read_text(), re.S).group(1)
    rules = parse_rules(front_matter)

    (args.dir / "Lenient.md").write_text(render(
        "Lenient", "0.1", False, "Lenient Policy",
        "A light-touch profile: every generally-applicable bundled rule is "
        "enabled, but each matched rule deducts only 0.1 point and nothing is "
        "prohibited. Use it for early-stage specs or exploratory reviews where "
        "you want findings surfaced without the score dropping far.",
        rules))
    (args.dir / "Pedantic.md").write_text(render(
        "Pedantic", "2.0", True, "Pedantic Policy",
        "A strict profile: every generally-applicable bundled rule is enabled, "
        "each matched rule deducts 2 points, and the security rules "
        "(SECURITY001, SECURITY002, SEC009) are PROHIBITED so any hit fails the "
        "spec outright. Use it as a release gate once a spec is expected to be "
        "clean.",
        rules))
    print(f"wrote {args.dir/'Lenient.md'} and {args.dir/'Pedantic.md'} ({len(rules)} rules each)")


if __name__ == "__main__":
    main()
