def _report(out, keys, allowed, pointer, where):
    for key in keys:
        if key not in allowed:
            out.append({"pointer": pointer, "path": where, "message": "Uses the non-standard extension '" + key + "'"})


def detect(api, rule):
    allowed = [token.strip() for token in str(rule["parameters"].get("allowed", "")).split(",") if token.strip()]
    out = []
    _report(out, api["info"].get("extensionKeys", []), allowed, "/info", "info")
    for path in api["paths"]:
        for op in path["operationDetails"]:
            _report(out, op.get("extensionKeys", []), allowed, op["pointer"], op["method"] + " " + path["path"])
    for schema in api["schemas"]:
        _report(out, schema.get("extensionKeys", []), allowed, schema["pointer"], schema["name"])
        for prop in schema["properties"]:
            _report(out, prop.get("extensionKeys", []), allowed, prop["pointer"], schema["name"] + "." + prop["name"])
    for path in api["paths"]:
        for op in path["operationDetails"]:
            for prm in op["parameters"]:
                _report(out, prm.get("extensionKeys", []), allowed, prm["pointer"], prm["name"])
    return out
