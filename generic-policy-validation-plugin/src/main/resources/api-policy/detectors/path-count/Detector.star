# Port of Detector.groovy (issue #125).

def _resource_segments(path_str):
    return [token for token in tokenize(path_str, "/") if not token.startswith("{")]

def _first_resource(path_str):
    segments = _resource_segments(path_str)
    return segments[0] if segments else None

def _distinct(values):
    seen = []
    for value in values:
        if value != None and value not in seen:
            seen.append(value)
    return seen

def detect(api, rule):
    p = rule["parameters"]

    if "maximum-depth" in p:
        limit = p["maximum-depth"]
        out = []
        for path in api["paths"]:
            if len(_resource_segments(path["path"])) > limit:
                out.append({"pointer": path["pointer"], "path": path["path"],
                            "message": "Resource path exceeds the maximum nesting depth"})
        return out

    if p.get("nested-root"):
        roots = _distinct([_first_resource(path["path"]) for path in api["paths"]])
        out = []
        for path in api["paths"]:
            parts = _resource_segments(path["path"])
            if len(parts) > 1 and parts[-1] not in roots:
                out.append({"pointer": path["pointer"], "path": path["path"],
                            "message": "Nested resource type is not exposed as a root resource"})
        return out

    resources = _distinct([_first_resource(path["path"]) for path in api["paths"]])
    if len(resources) > p["maximum"]:
        return [{"pointer": "/paths", "path": "API",
                 "message": "API has " + str(len(resources)) + " top-level resource types (maximum " + str(p["maximum"]) + ")"}]
    return []
