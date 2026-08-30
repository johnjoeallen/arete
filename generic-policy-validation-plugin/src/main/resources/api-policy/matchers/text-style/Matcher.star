# Port of Matcher.groovy (issue #125). The ALL-CAPS regex keeps the original's
# doubled backslashes verbatim so results match the Groovy rule exactly.
_ALL_CAPS_WORD = r".*\\b[A-Z]{2,}\\b.*"
_ACTION_VERBS = ["Get", "List", "Create", "Update", "Delete", "Replace", "Search",
                 "Find", "Cancel", "Activate", "Deactivate"]

def _matches(op, p):
    summary = op["summary"]
    if summary == None:
        return False
    summary = summary.strip()
    if not summary:
        return False

    if "initial-capital" in p:
        has_initial_capital = summary[0].isupper()
        if has_initial_capital != p["initial-capital"]:
            return False
    if p.get("convention") == "sentence-case":
        if summary[0].isupper() and not re_fullmatch(_ALL_CAPS_WORD, summary):
            return False
    if p.get("trailing-period") == "present" and not summary.endswith("."):
        return False
    if p.get("trailing-period") == "absent" and summary.endswith("."):
        return False
    if "maximum-length" in p and len(summary) <= p["maximum-length"]:
        return False
    if p.get("match") == "non-action-oriented" and any(
            [summary == verb or summary.startswith(verb + " ") for verb in _ACTION_VERBS]):
        return False
    return True

def _message(p):
    if "initial-capital" in p:
        return "Operation summary does not begin with a capital letter"
    if p.get("convention") == "sentence-case":
        return "Operation summary is not sentence case"
    if p.get("trailing-period") == "present":
        return "Operation summary ends with a period"
    if "maximum-length" in p:
        return "Operation summary exceeds the configured maximum length"
    if p.get("match") == "non-action-oriented":
        return "Operation summary is not action-oriented"
    return "Operation summary matches the configured style rule"

def detect(api, rule):
    p = rule["parameters"]
    message = _message(p)
    out = []
    for path in api["paths"]:
        for op in path["operationDetails"]:
            if _matches(op, p):
                out.append({
                    "pointer": op["pointer"],
                    "path": op["method"] + " " + path["path"],
                    "message": message,
                })
    return out
