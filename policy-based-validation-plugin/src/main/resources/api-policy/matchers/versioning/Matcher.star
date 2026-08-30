# Port of rules/versioning/Matcher.groovy (issue #125 POC).
# Versioning facts derived only from the stable rule map.

_URI = r".*/(v[0-9]+|version[0-9]+)(/.*)?"
_HEADER = r"(?i)(api[-_])?version|x-api-version"
_MEDIA = r"(?i).*\+?v[0-9]+.*|.*version[0-9]+.*"

def _version_uri(path):
    return re_fullmatch(_URI, path["path"])

def _version_header(op):
    for parameter in op["parameters"]:
        if parameter["in"] == "header" and re_fullmatch(_HEADER, parameter["name"]):
            return True
    return False

def _version_media(op):
    for media_type in op["mediaTypes"]:
        if re_fullmatch(_MEDIA, media_type):
            return True
    return False

def _found(path, location):
    if location == "uri":
        return _version_uri(path)
    if location == "header":
        return any([_version_header(op) for op in path["operationDetails"]])
    if location == "media-type":
        return any([_version_media(op) for op in path["operationDetails"]])
    return False

def _any_version(path):
    if _version_uri(path):
        return True
    for op in path["operationDetails"]:
        if _version_header(op) or _version_media(op):
            return True
    return False

def detect(api, rule):
    p = rule["parameters"]
    location = p.get("location")

    if p.get("match") == "absent":
        for path in api["paths"]:
            if _any_version(path):
                return []
        return [{"pointer": "/paths", "path": "API", "message": "Interface has no explicit versioning"}]

    out = []
    for path in api["paths"]:
        if _found(path, location):
            out.append({
                "pointer": path["pointer"],
                "path": path["path"],
                "message": "Interface version is exposed through " + location,
            })
    return out
