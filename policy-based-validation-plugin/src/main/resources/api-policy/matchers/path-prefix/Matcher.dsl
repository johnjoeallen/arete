distill(api, rule) {
    return (size(api.paths) > 1
        && size(api.paths.filter { p -> size(pathSegments(p.path)) > 0 }) == size(api.paths)
        && size(distinct(api.paths.map { p -> pathSegments(p.path)[0] })) == 1)
        ? [occurrence("/paths", "API",
            "All " + ("" + size(api.paths)) + " paths start with '/"
                + pathSegments(api.paths[0].path)[0]
                + "', which could move to the server URL")]
        : [];
}
