# Port of Detector.groovy (issue #125).
def detect(api, rule):
    raw = rule["parameters"].get("allowed") or ""
    allowed = [token.strip() for token in raw.split(",") if token.strip()]
    version = api["info"].get("openapiVersion")
    supported = version != None and any([version == a or version.startswith(a + ".") for a in allowed])
    if supported:
        return []
    return [{
        "pointer": "/info",
        "path": "API",
        "message": "Document declares unsupported or missing OpenAPI version: " + (version or "none"),
    }]
