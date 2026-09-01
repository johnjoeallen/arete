# Documentation generators

Helpers for keeping the docs site (`docs/`) in sync with the code.

## `gen-policy-docs.py`

Regenerates `docs/validation/rules.md` and `docs/validation/policies.md` from
the policy bundle
(`arete-policy-plugin/src/main/resources/api-policy/`). Run it
after adding or changing a rule, policy, or rule.

```bash
pip install pyyaml
python3 scripts/docs/gen-policy-docs.py
```

Both files carry a "do not edit by hand" marker — edit the bundle, then rerun.

## `gen-screenshots.py`

Recaptures the app screenshots in `docs/assets/` by driving a running
Areté instance with Playwright, using `bookstore-demo.yaml` as the sample
spec.

```bash
pip install playwright && playwright install chromium

# Start a throwaway instance so it can't touch a real spec collection:
HOME=$(mktemp -d) java -jar scripts/arete.jar --server.port=6810 &
ARETE_URL=http://localhost:6810 python3 scripts/docs/gen-screenshots.py
```

Captures `screenshot.png` (Explore), `screenshot-validation.png`,
`screenshot-model.png`, `screenshot-general.png`, and
`screenshot-settings.png`.
