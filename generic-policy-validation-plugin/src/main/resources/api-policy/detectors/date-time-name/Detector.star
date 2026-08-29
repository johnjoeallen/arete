# Port of Detector.groovy (issue #125).
def detect(api, rule):
    suffix = rule["parameters"]["suffix"]
    out = []
    for schema in api["schemas"]:
        for prop in schema["properties"]:
            if prop["type"] == "string" and prop["format"] == "date-time" and not prop["name"].endswith(suffix):
                out.append({
                    "pointer": prop["pointer"],
                    "path": prop["name"],
                    "message": "Date-time property name does not end with " + suffix,
                })
    return out
