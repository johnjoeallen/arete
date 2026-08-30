# Port of Matcher.groovy (issue #125).
def detect(api, rule):
    out = []
    for schema in api["schemas"]:
        for prop in schema["properties"]:
            name = prop["name"]
            inconsistent = (
                (name == "id" and prop["type"] != "string")
                or ((name == "created" or name == "modified")
                    and not (prop["type"] == "string" and prop["format"] == "date-time"))
            )
            if inconsistent:
                out.append({
                    "pointer": prop["pointer"],
                    "path": name,
                    "message": "Common field has an inconsistent type or format",
                })
    return out
