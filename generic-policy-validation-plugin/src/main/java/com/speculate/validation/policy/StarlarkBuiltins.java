package com.speculate.validation.policy;

import com.google.re2j.Pattern;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkValue;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

/**
 * POC (issue #125) — the complete capability surface exposed to a Starlark
 * detector beyond core list/dict/string operations.
 *
 * <p>Regex is backed by {@link com.google.re2j RE2/J}: linear-time matching,
 * no catastrophic backtracking, no lookaround or backreferences. That makes a
 * detector-supplied (and, later, remote-bundle-supplied) pattern safe to
 * compile and run against attacker-influenced input.
 */
public final class StarlarkBuiltins implements StarlarkValue {

    private final ConcurrentHashMap<String, Pattern> patternCache = new ConcurrentHashMap<>();

    private Pattern pattern(String regex) {
        return patternCache.computeIfAbsent(regex, Pattern::compile);
    }

    @StarlarkMethod(
            name = "re_fullmatch",
            doc = "True if the whole text matches the RE2 pattern.",
            parameters = {
                    @Param(name = "pattern"),
                    @Param(name = "text"),
            })
    public boolean reFullmatch(String pattern, String text) {
        return pattern(pattern).matches(text);
    }

    @StarlarkMethod(
            name = "re_search",
            doc = "True if the RE2 pattern matches anywhere in the text.",
            parameters = {
                    @Param(name = "pattern"),
                    @Param(name = "text"),
            })
    public boolean reSearch(String pattern, String text) {
        return pattern(pattern).matcher(text).find();
    }

    @StarlarkMethod(
            name = "tokenize",
            doc = "Splits text on any character in delims, dropping empty tokens (Groovy String.tokenize semantics).",
            parameters = {
                    @Param(name = "text"),
                    @Param(name = "delims"),
            })
    public StarlarkList<String> tokenize(String text, String delims) {
        StringTokenizer tokenizer = new StringTokenizer(text, delims);
        List<String> tokens = new ArrayList<>();
        while (tokenizer.hasMoreTokens()) {
            tokens.add(tokenizer.nextToken());
        }
        return StarlarkList.immutableCopyOf(tokens);
    }

    @StarlarkMethod(
            name = "parse_int",
            doc = "Parses text as a base-10 integer, or returns fallback if it is not one (Groovy Integer.parseInt + catch).",
            parameters = {
                    @Param(name = "text"),
                    @Param(name = "fallback"),
            })
    public StarlarkInt parseInt(String text, StarlarkInt fallback) {
        try {
            return StarlarkInt.of(Integer.parseInt(text));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    @StarlarkMethod(
            name = "url_host",
            doc = "Host component of a URL, or None if it cannot be parsed.",
            parameters = {@Param(name = "url")},
            allowReturnNones = true)
    public Object urlHost(String url) {
        try {
            String host = new java.net.URI(url).getHost();
            return host == null ? Starlark.NONE : host;
        } catch (Exception e) {
            return Starlark.NONE;
        }
    }
}
