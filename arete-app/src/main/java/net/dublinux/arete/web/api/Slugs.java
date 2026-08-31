package net.dublinux.arete.web.api;

import java.util.regex.Pattern;

/**
 * Namespace and submitter are plain caller-asserted slugs — not credentials,
 * not security boundaries. This only normalises and shape-checks them so they
 * are safe to store, index, and put in a URL path.
 */
public final class Slugs {

    private static final Pattern SLUG = Pattern.compile("[a-z0-9](?:[a-z0-9._-]{0,62})");

    private Slugs() {
    }

    /** Lower-cases and trims; returns null if the value is null/blank. */
    public static String normalise(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toLowerCase();
        return s.isEmpty() ? null : s;
    }

    public static boolean isValid(String slug) {
        return slug != null && SLUG.matcher(slug).matches();
    }

    /**
     * Best-effort coercion to a valid slug for UI convenience — lower-case,
     * runs of anything outside {@code [a-z0-9._-]} become a single {@code -},
     * trimmed of leading/trailing {@code -} / {@code .}, capped at 63 chars.
     * Returns null if nothing usable is left.
     */
    public static String slugify(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toLowerCase()
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^[-.]+", "")
                .replaceAll("[-.]+$", "");
        if (s.length() > 63) {
            s = s.substring(0, 63).replaceAll("[-.]+$", "");
        }
        return isValid(s) ? s : null;
    }

    /** Normalise and validate, or throw {@link SlugException} (which the API maps to 422). */
    public static String require(String raw, String what) {
        String slug = normalise(raw);
        if (!isValid(slug)) {
            throw new SlugException(what + " must match [a-z0-9][a-z0-9._-]{0,62} (got: "
                    + (raw == null ? "none" : "'" + raw + "'") + ")");
        }
        return slug;
    }

    public static final class SlugException extends RuntimeException {
        public SlugException(String message) {
            super(message);
        }
    }
}
