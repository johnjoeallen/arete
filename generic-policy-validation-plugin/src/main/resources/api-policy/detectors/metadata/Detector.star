# Port of Detector.groovy (issue #125). The semver regex keeps the original's
# doubled backslashes verbatim so results match the Groovy detector exactly.
_SEMVER = r"0|[1-9][0-9]*\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?(?:\\+[0-9A-Za-z.-]+)?"

def _blank(value):
    return type(value) != "string" or value.strip() == ""

def detect(api, rule):
    info = api["info"]
    required = rule["parameters"].get("required")

    if required == "identifier":
        if _blank(info.get("apiId")):
            return [{"pointer": "/", "path": "API", "message": "API metadata is missing x-api-id"}]
        return []
    if required == "audience":
        if _blank(info.get("audience")):
            return [{"pointer": "/", "path": "API", "message": "API metadata is missing x-audience"}]
        return []

    missing = []
    for key, label in [("title", "title"), ("description", "description"),
                       ("contactName", "contact name"), ("contactEmail", "contact email")]:
        if _blank(info.get(key)):
            missing.append(label)
    version = info.get("version")
    if type(version) != "string" or not re_fullmatch(_SEMVER, version):
        missing.append("semantic version")

    return [{"pointer": "/info", "path": "API", "message": "API metadata is missing " + field}
            for field in missing]
