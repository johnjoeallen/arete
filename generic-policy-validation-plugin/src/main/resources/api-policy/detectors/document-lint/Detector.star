def detect(api, rule):
    p = rule["parameters"]
    check = p["check"]
    lint = api.get("lint", {})
    out = []
    if check == "parser-message":
        pattern = p.get("pattern")
        if not pattern:
            return out
        for message in lint.get("parserMessages", []):
            if re_search(pattern, message):
                out.append({"pointer": "/", "path": "document", "message": "Parser reported: " + message})
    elif check == "numeric-status-key":
        codes = lint.get("numericStatusKeys", [])
        if len(codes) > 0:
            out.append({
                "pointer": "/paths",
                "path": "document",
                "message": "HTTP status keys are declared as bare numbers, not strings: " + ", ".join(codes),
            })
    return out
