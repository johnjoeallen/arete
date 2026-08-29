def _matches(name, pattern):
    return re_search("(?i)" + pattern, name)

def _collection_get(path, op):
    return op["method"] == "GET" and "{" not in path["path"]

def _status(value):
    return parse_int(str(value), -1)

def _has_header(headers, expected):
    expected = expected.lower()
    for header in headers:
        if str(header).lower() == expected:
            return True
    return False

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
                                "message": "Collection operation lacks the configured pagination control"})
                continue
            if rule["scope"] == "query-parameter":
                for param in params:
                    bad = p["check"] == "integer" and param["schemaType"] != "integer"
                    bad = bad or (p["check"] == "string" and param["schemaType"] != "string")
                    bad = bad or (p["check"] == "maximum" and (param["schemaMaximum"] == None or param["schemaMaximum"] > p["maximum"]))
                    if bad:
                        out.append({"pointer": param["pointer"], "path": location,
                                    "message": "Pagination parameter does not meet the configured constraint: " + param["name"]})
                continue
            for resp in op["responses"]:
                code = _status(resp["status"])
                if _collection_get(path, op) and code >= 200 and code < 300 and not _has_header(resp["headers"], "Link"):
                    out.append({"pointer": op["pointer"], "path": location,
                                "message": "Successful paginated response lacks a Link header"})
    return out
