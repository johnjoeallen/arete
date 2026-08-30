def _matches(name, pattern):
    return re_search("(?i)" + pattern, name)

def detect(api, rule):
    p = rule["parameters"]
    out = []
    for schema in api["schemas"]:
        for prop in schema["properties"]:
            if not _matches(prop["name"], p["name-pattern"]):
                continue
            if p["check"] == "string" and prop["type"] != "string":
                out.append({"pointer": prop["pointer"], "path": prop["name"],
                            "message": "Identifier property should use type string: " + prop["name"]})
            elif p["check"] == "format" and prop["format"] != p.get("format"):
                out.append({"pointer": prop["pointer"], "path": prop["name"],
                            "message": "Identifier property should declare format " + p.get("format") + ": " + prop["name"]})
    return out
