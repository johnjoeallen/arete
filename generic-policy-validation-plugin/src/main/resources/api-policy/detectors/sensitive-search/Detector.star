def _matches(name, pattern):
    return re_search("(?i)" + pattern, name)

def detect(api, rule):
    p = rule["parameters"]
    search_pattern = p["search-pattern"]
    sensitive_pattern = p["sensitive-pattern"]
    out = []
    for path in api["paths"]:
        for op in path["operationDetails"]:
            location = op["method"] + " " + path["path"]
            query = [param for param in op["parameters"] if param["in"] == "query"]
            search = [param for param in query if _matches(param["name"], search_pattern)]
            sensitive = [param for param in query if _matches(param["name"], sensitive_pattern)]
            if rule["scope"] == "query-parameter":
                for param in search:
                    out.append({"pointer": param["pointer"], "path": location,
                                "message": "Search query parameter may carry sensitive data: " + param["name"]})
            elif search and sensitive:
                out.append({"pointer": op["pointer"], "path": location,
                            "message": "Operation permits searching sensitive query data"})
    return out
