# Port of rules/operation-semantics/Matcher.groovy (issue #125 POC).
# Conservative textual signals only; an OpenAPI contract cannot prove runtime
# behaviour, so messages say "appears" / "may".

_MUTATION = r"(?i).*\b(create|update|delete|remove|activate|deactivate|cancel|change|set)\b.*"
_REPLACEMENT = r"(?i).*\b(replace|replacement)\b.*"
_PARTIAL = r"(?i).*\b(partial|patch|update)\b.*"
_IDENTIFIED_RESOURCE = r".+/\{[^}]+\}.*"

def _matches(path, op, p):
    method = p.get("method")
    if method and op["method"] != method:
        return False

    text = ((path["path"] or "") + " " + (op["summary"] or "")).strip()
    get_mutation = op["method"] == "GET" and re_fullmatch(_MUTATION, text)
    post_replacement = (op["method"] == "POST"
                        and re_fullmatch(_IDENTIFIED_RESOURCE, path["path"])
                        and re_fullmatch(_REPLACEMENT, text))
    put_partial = op["method"] == "PUT" and re_fullmatch(_PARTIAL, text)

    if p.get("expected") == "safe":
        return get_mutation

    match = p.get("match")
    if match == "full-resource-replacement":
        return post_replacement
    if match == "partial-update":
        return put_partial
    if match == "inconsistent-method-resource-semantics":
        return get_mutation or post_replacement
    return False

def _message(p):
    if p.get("expected") == "safe":
        return "GET operation appears to mutate state"
    match = p.get("match")
    if match == "full-resource-replacement":
        return "POST appears to replace an identified resource"
    if match == "partial-update":
        return "PUT appears to perform a partial update"
    if match == "inconsistent-method-resource-semantics":
        return "HTTP method and resource semantics appear inconsistent"
    return "Supported operation semantics are unclear"

def detect(api, rule):
    p = rule["parameters"]
    message = _message(p)
    out = []
    for path in api["paths"]:
        for op in path["operationDetails"]:
            if _matches(path, op, p):
                out.append({
                    "pointer": op["pointer"],
                    "path": op["method"] + " " + path["path"],
                    "message": message,
                })
    return out
