# Port of Detector.groovy (issue #125).
def detect(api, rule):
    p = rule["parameters"]
    out = []
    for path in api["paths"]:
        for op in path["operationDetails"]:
            text = ((path["path"] or "") + " " + (op["summary"] or "")).lower()
            matched = False
            if p.get("operation-type") == "create":
                create_like = ("create" in text) or ("bulk" in text)
                matched = create_like and (op["method"] != p.get("expected-method") or "{" in path["path"])
            elif p.get("target-selection") == "search-criteria":
                matched = op["method"] == p.get("method") and re_search(r"(?i)(search|filter|criteria|query)", text)
            if matched:
                if p.get("operation-type") == "create":
                    message = "Bulk creation is not POSTed to a collection"
                else:
                    message = "Bulk mutation uses search criteria"
                out.append({
                    "pointer": op["pointer"],
                    "path": op["method"] + " " + path["path"],
                    "message": message,
                })
    return out
