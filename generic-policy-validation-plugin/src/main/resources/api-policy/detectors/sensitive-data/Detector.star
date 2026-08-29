def _matches(name, pattern):
    return re_search("(?i)" + pattern, name)

def detect(api, rule):
    p = rule["parameters"]
    pattern = p["pattern"]
    scope = rule["scope"]
    out = []
    if scope == "property":
        for schema in api["schemas"]:
            for prop in schema["properties"]:
                if _matches(prop["name"], pattern):
                    out.append({"pointer": prop["pointer"], "path": prop["name"],
                                "message": "Schema property name may contain sensitive data: " + prop["name"]})
        return out
    wanted = "query" if scope == "query-parameter" else "path" if scope == "path-parameter" else "header"
    for path in api["paths"]:
        for op in path["operationDetails"]:
            for param in op["parameters"]:
                if param["in"] == wanted and _matches(param["name"], pattern):
                    out.append({"pointer": param["pointer"], "path": op["method"] + " " + path["path"],
                                "message": "Parameter name may expose sensitive data: " + param["name"]})
    return out
