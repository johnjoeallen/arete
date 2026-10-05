package net.dublinux.arete.engine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Small policy bundles built from the real rules and matchers, for the source and override tests. */
final class MiniBundle {
    private MiniBundle() { }

    static String resource(String path) {
        try (InputStream in = MiniBundle.class.getClassLoader().getResourceAsStream("api-policy/" + path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** DOC001 (summary missing) on the {@code operation} matcher, in one policy named {@code Mini}. */
    static Map<String, String> base(String version) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("PolicyBundle.yaml", "formatVersion: 1\nbundleId: mini\nbundleVersion: " + version
                + "\nrules:\n  DOC001: rules/DOC001.md\npolicies:\n  Mini: policies/Mini.md\nmatchers:\n  operation: matchers/operation/Matcher.md\n");
        files.put("rules/DOC001.md", resource("rules/DOC001.md"));
        files.put("matchers/operation/Matcher.md", resource("matchers/operation/Matcher.md"));
        files.put("matchers/operation/Matcher.distill", resource("matchers/operation/Matcher.distill"));
        files.put("policies/Mini.md", "---\nid: Mini\nrules:\n  DOC001: 1\n---\n\n# Mini\n");
        return files;
    }

    /** Adds DOC015 (description missing, on the base's {@code operation} matcher) and replaces {@code Mini} to run both, DOC001 locked. */
    static Map<String, String> overlay(String version) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("PolicyBundle.yaml", "formatVersion: 1\nbundleId: overlay\nbundleVersion: " + version
                + "\nrules:\n  DOC015: rules/DOC015.md\npolicies:\n  Mini: policies/Mini.md\n");
        files.put("rules/DOC015.md", resource("rules/DOC015.md"));
        files.put("policies/Mini.md", "---\nid: Mini\nrules:\n  DOC001:\n    points: 1\n    locked: true\n  DOC015: 2\n---\n\n# Mini\n");
        return files;
    }

    static Path writeDir(Path dir, Map<String, String> files) {
        try {
            for (Map.Entry<String, String> file : files.entrySet()) {
                Path target = dir.resolve(file.getKey());
                Files.createDirectories(target.getParent());
                Files.writeString(target, file.getValue(), StandardCharsets.UTF_8);
            }
            return dir;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static byte[] zip(Map<String, String> files, String folder) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(out)) {
                for (Map.Entry<String, String> file : files.entrySet()) {
                    zip.putNextEntry(new ZipEntry(folder + file.getKey()));
                    zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                    zip.closeEntry();
                }
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static final String SPEC = """
            openapi: 3.0.0
            info: { title: T, version: 1.0.0 }
            paths:
              /customers:
                get:
                  responses: { '200': { description: OK } }
            """;
}
