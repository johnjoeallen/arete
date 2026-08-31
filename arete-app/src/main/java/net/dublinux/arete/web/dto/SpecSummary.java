package net.dublinux.arete.web.dto;

/** Sidebar/list row. {@code ref} is the public UUID used in every URL; the numeric id is not exposed. */
public record SpecSummary(String ref, String title, long updatedAtMillis) {
}
