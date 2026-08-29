def _matches(name, pattern):
    return re_search("(?i)" + pattern, name)

def _collection_get(path, op):
    return op["method"] == "GET" and "{" not in path["path"]

def detect(api, rule):
    p = rule["parameters"]
    out = []
    for path in api["paths"]:
        for op in path["operationDetails"]:
            location = op["method"] + " " + path["path"]
            params = [param for param in op["parameters"] if param["in"] == "query" and _matches(param["name"], p["name-pattern"])]
            if rule["scope"] == "operation":
                if _collection_get(path, op) and not params:
                    out.append({"pointer": op["pointer"], "path": location,
                                "message": "Collection operation lacks the configured query capability"})
                continue
            for param in params:
                bad = (p["check"] == "string" and param["schemaType"] != "string")
                bad = bad or (p["check"] == "array" and param["schemaType"] != "array")
                bad = bad or (p["check"] == "form" and param["style"] not in (None, "form"))
                if bad:
                    out.append({"pointer": param["pointer"], "path": location,
                                "message": "Collection query capability does not use the configured representation: " + param["name"]})
    return out
