def _normalize(path):
    parts = []
    for segment in path.split("/"):
        if segment.startswith("{") and segment.endswith("}"):
            parts.append("{}")
        else:
            parts.append(segment)
    return "/".join(parts)


def detect(api, rule):
    out = []
    seen = {}
    for path in api["paths"]:
        normalized = _normalize(path["path"])
        if normalized in seen:
            out.append({
                "pointer": path["pointer"],
                "path": path["path"],
                "message": "Path is structurally identical to " + seen[normalized],
            })
        else:
            seen[normalized] = path["path"]
    return out
