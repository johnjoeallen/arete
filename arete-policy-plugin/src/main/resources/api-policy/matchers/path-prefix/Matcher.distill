distill(api, rule) {
    return (count(api.paths) > 1
        && count(api.paths.filter { p -> count(pathSegments(p.path)) > 0 }) == count(api.paths)
        && count(distinct(api.paths.map { p -> pathSegments(p.path)[0] })) == 1)
        ? [occurrence("/paths", "API",
            "All " + ("" + count(api.paths)) + " paths start with '/"
                + pathSegments(api.paths[0].path)[0]
                + "', which could move to the server URL")]
        : [];
}
