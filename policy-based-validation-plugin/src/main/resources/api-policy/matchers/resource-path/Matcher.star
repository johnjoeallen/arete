# Port of rules/resource-path/Matcher.groovy (issue #125 POC).
# Reports diagnostics only; never a score, severity, or policy decision.

_VERBS = ["get", "list", "create", "update", "delete", "remove", "add", "set"]

def _terminal_segment(path_str):
    tokens = tokenize(path_str, "/")
    return tokens[-1] if tokens else ""

def _matches(path, match):
    path_str = path["path"]
    terminal = _terminal_segment(path_str)
    lower = terminal.lower()

    if match == "operation-verb":
        return any([lower.startswith(verb) for verb in _VERBS])
    if match == "query-predicate":
        return re_fullmatch(r"(?i).*(find|get|search)By[A-Z].*", terminal)
    if match == "rpc-style":
        return len(tokenize(path_str, "/")) > 1 and lower in _VERBS
    if match == "custom-action":
        return re_fullmatch(r"(?i).*/actions(?:/[^/]+)?", path_str)
    if match == "action-style":
        return (re_fullmatch(r"(?i).*/actions(?:/[^/]+)?", path_str)
                or any([lower.startswith(verb) for verb in _VERBS]))
    return False

def _message(match):
    if match == "query-predicate":
        return "Resource path contains a query predicate"
    if match == "rpc-style":
        return "API uses RPC-style resource design"
    if match == "custom-action":
        return "Custom action resource is used"
    if match == "action-style":
        return "Action-style endpoint is used"
    return "Resource path contains an operation verb"

def detect(api, rule):
    match = rule["parameters"].get("match")

    if match == "trailing-slash":
        out = []
        for path in api["paths"]:
            path_str = path["path"]
            if len(path_str) > 1 and path_str.endswith("/"):
                out.append({
                    "pointer": path["pointer"],
                    "path": path_str,
                    "message": "Resource path has an unnecessary trailing slash",
                })
        return out

    if match == "embedded-identifier":
        out = []
        for path in api["paths"]:
            embedded = False
            for segment in tokenize(path["path"], "/"):
                if re_fullmatch(r".*(?:Id|ID|[0-9]{2,}).*", segment) and not segment.startswith("{"):
                    embedded = True
            if embedded:
                out.append({
                    "pointer": path["pointer"],
                    "path": path["path"],
                    "message": "Resource identifier is embedded in a path segment",
                })
        return out

    message = _message(match)
    out = []
    for path in api["paths"]:
        if _matches(path, match):
            for method in path["operations"]:
                out.append({
                    "pointer": path["pointer"] + "/" + method.lower(),
                    "path": method + " " + path["path"],
                    "message": message,
                })
    return out
