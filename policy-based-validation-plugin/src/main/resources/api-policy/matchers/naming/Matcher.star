# Port of Matcher.groovy (issue #125).

def _candidates(api, scope):
    if scope == "property":
        return [prop for schema in api["schemas"] for prop in schema["properties"]]
    if scope == "schema":
        return api["schemas"]
    if scope == "path-segment":
        return [segment for path in api["paths"] for segment in path["segments"]]
    if scope == "path-parameter":
        return [pm for path in api["paths"] for op in path["operationDetails"]
                for pm in op["parameters"] if pm["in"] == "path"]
    if scope == "query-parameter":
        return [pm for path in api["paths"] for op in path["operationDetails"]
                for pm in op["parameters"] if pm["in"] == "query"]
    if scope == "header":
        return [pm for path in api["paths"] for op in path["operationDetails"]
                for pm in op["parameters"] if pm["in"] == "header"]
    return []

def _conforms(name, convention):
    if convention == "camelCase":
        return re_fullmatch(r"[a-z][A-Za-z0-9]*", name)
    if convention == "snake_case":
        return re_fullmatch(r"[a-z][a-z0-9]*(?:_[a-z0-9]+)*", name)
    if convention == "kebab-case":
        return re_fullmatch(r"[a-z][a-z0-9]*(?:-[a-z0-9]+)*", name)
    if convention == "hyphenated":
        return re_fullmatch(r"[A-Za-z0-9]+(?:-[A-Za-z0-9]+)+", name)
    return False

def _plural(name):
    return name.lower().endswith("s") and len(name) > 1

def _matches(candidate, p):
    name = candidate["name"]
    if p.get("convention") and p.get("match") == "non-conforming" and _conforms(name, p["convention"]):
        return False
    if p.get("match") == "unsupported-character" and re_fullmatch(r"[A-Za-z][A-Za-z0-9_-]*", name):
        return False
    if p.get("suffix") and p.get("match") == "present" and not name.endswith(p["suffix"]):
        return False
    if p.get("semantic") == "collection" and _plural(name):
        return False
    if p.get("semantic") == "singular" and _plural(name):
        return False
    if p.get("semantic") == "plural" and not _plural(name):
        return False
    if p.get("schema-type") == "array" and candidate.get("type") != "array":
        return False
    return True

def _message(p):
    if p.get("suffix"):
        return "Name has prohibited suffix " + p["suffix"]
    if p.get("semantic") == "collection":
        return "Collection name is singular"
    if p.get("semantic") == "singular":
        return "Array property has a singular name"
    if p.get("match") == "unsupported-character":
        return "Name contains unsupported characters"
    return "Name does not use the configured convention"

def detect(api, rule):
    p = rule["parameters"]
    message = _message(p)
    return [{"pointer": candidate["pointer"], "path": candidate["name"], "message": message}
            for candidate in _candidates(api, rule["scope"]) if _matches(candidate, p)]
