package net.dublinux.arete.validation.policy;

import com.google.re2j.Pattern;
import net.dublinux.arete.validation.policy.Diagnostic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

/** Interpreter for Distill, the Java-shaped fluent rule language (Matcher.dsl). */
public final class DistillMatcherEvaluator {
    public List<Diagnostic> execute(String source, Map<String, Object> api, Map<String, Object> rule) {
        return new Parser(source).parse().apply(Map.of("api", api, "rule", rule));
    }

    /** Parses the rule source, failing the bundle load on a syntax error. */
    void validate(Matcher rule) {
        if (!"distill".equals(rule.language())) {
            throw new BundleValidationException("Unsupported rule language '" + rule.language() + "'");
        }
        try {
            new Parser(rule.source()).parse();
        } catch (RuntimeException e) {
            throw new BundleValidationException("Matcher '" + rule.id() + "' does not compile: " + e.getMessage());
        }
    }

    List<Diagnostic> execute(Matcher matcher, Map<String, Object> api, PolicyRule rule) {
        if (!"distill".equals(matcher.language())) {
            throw new MatcherEvaluationException("Unsupported rule language '" + matcher.language() + "'");
        }
        try {
            List<Diagnostic> diagnostics = execute(matcher.source(), api, rule.asMap());
            if (diagnostics.size() > 1_000) throw new MatcherEvaluationException("Matcher returned more than 1000 diagnostics");
            return diagnostics;
        } catch (MatcherEvaluationException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new MatcherEvaluationException(e.toString(), e);
        }
    }

    private interface Expr { Object eval(Map<String, Object> env); }
    private interface Closure { Object apply(Object value); }
    private record Program(Expr result) { List<Diagnostic> apply(Map<String, Object> env) { return castDiagnostics(result.eval(env)); } }
    private record ParsedClosure(String parameter, Expr body) { }

    private static List<Diagnostic> castDiagnostics(Object value) {
        if (!(value instanceof Iterable<?> values)) throw new IllegalArgumentException("rule must return a collection");
        List<Diagnostic> result = new ArrayList<>();
        for (Object item : values) {
            if (!(item instanceof Diagnostic diagnostic)) throw new IllegalArgumentException("rule returned a non-occurrence");
            result.add(diagnostic);
        }
        return result;
    }

    private static Object member(Object value, String name) {
        if (value == null) return null;
        if (value instanceof Map<?, ?> map) {
            if (map.containsKey(name)) return map.get(name);
            if (name.equals("keys")) return new ArrayList<Object>(map.keySet());
            if (name.equals("values")) return new ArrayList<Object>(map.values());
            return null;
        }
        if (value instanceof String text) return switch (name) {
            case "length" -> text.length();
            default -> null;
        };
        return null;
    }

    /** {@code value[key]}: string keys index maps, integer keys index lists (negative counts from the end). */
    private static Object index(Object value, Object key) {
        if (value == null) return null;
        if (value instanceof Map<?, ?> map) return map.get(String.valueOf(key));
        if (value instanceof Iterable<?> && key instanceof Number number) {
            List<Object> list = iterableOf(value);
            int position = number.intValue();
            if (position < 0) position += list.size();
            return position >= 0 && position < list.size() ? list.get(position) : null;
        }
        if (value instanceof String text) return member(text, String.valueOf(key));
        return null;
    }

    private static Object call(Object receiver, String name, List<Object> args) {
        if (receiver instanceof String text) return switch (name) {
            case "lower" -> text.toLowerCase();
            case "trim" -> text.trim();
            case "contains" -> text.contains(String.valueOf(args.get(0)));
            case "startsWith" -> text.startsWith(String.valueOf(args.get(0)));
            case "endsWith" -> text.endsWith(String.valueOf(args.get(0)));
            default -> throw new IllegalArgumentException("unknown string operation: " + name);
        };
        if (receiver instanceof Iterable<?> iterable) {
            List<Object> values = new ArrayList<>();
            iterable.forEach(values::add);
            Closure closure = args.isEmpty() ? null : (Closure) args.get(0);
            return switch (name) {
                case "map" -> values.stream().map(closure::apply).toList();
                case "filter", "match" -> values.stream().filter(value -> truthy(closure.apply(value))).toList();
                case "expand" -> values.stream().flatMap(value -> iterableOf(closure.apply(value)).stream()).toList();
                case "any" -> values.stream().anyMatch(value -> truthy(closure.apply(value)));
                case "all" -> values.stream().allMatch(value -> truthy(closure.apply(value)));
                case "find" -> values.stream().filter(value -> truthy(closure.apply(value))).findFirst().orElse(null);
                case "count" -> values.stream().filter(value -> truthy(closure.apply(value))).count();
                case "group" -> {
                    // key -> items with that key, in first-seen key order. Keys are
                    // compared by string value, matching distinct().
                    Map<String, List<Object>> groups = new LinkedHashMap<>();
                    for (Object value : values) {
                        groups.computeIfAbsent(String.valueOf(closure.apply(value)), k -> new ArrayList<>()).add(value);
                    }
                    yield groups;
                }
                case "toList" -> List.copyOf(values);
                default -> throw new IllegalArgumentException("unknown sequence operation: " + name);
            };
        }
        throw new IllegalArgumentException("cannot call " + name + " on " + receiver);
    }

    private static List<Object> iterableOf(Object value) {
        if (value == null) return List.of();
        if (value instanceof Iterable<?> iterable) { List<Object> result = new ArrayList<>(); iterable.forEach(result::add); return result; }
        throw new IllegalArgumentException("expand closure must return a collection");
    }

    private static boolean truthy(Object value) { return value instanceof Boolean b ? b : value != null; }

    private static boolean isBlank(Object value) { return value == null || value instanceof String text && text.trim().isEmpty(); }

    /** Ordering for {@code < <= > >=}: numeric by value, otherwise lexicographic. */
    private static int compare(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) return Double.compare(x.doubleValue(), y.doubleValue());
        if (a instanceof String x && b instanceof String y) return x.compareTo(y);
        throw new IllegalArgumentException("cannot order " + a + " and " + b);
    }

    /** {@code ==} / {@code !=}: numbers compare by value so Integer 8 equals Long 8. */
    private static boolean equalValue(Object a, Object b) {
        if (a instanceof Number x && b instanceof Number y) return x.doubleValue() == y.doubleValue();
        return Objects.equals(a, b);
    }

    /** A {@code ~/pattern/} literal; stringifies to the raw pattern. */
    private record Regex(String pattern) { public String toString() { return pattern; } }

    private static String patternOf(Object value) { return value instanceof Regex regex ? regex.pattern() : String.valueOf(value); }

    private static boolean regexSearch(Object pattern, Object text) { return Pattern.compile(patternOf(pattern)).matcher(String.valueOf(text)).find(); }

    private static boolean regexFullMatch(Object pattern, Object text) { return Pattern.compile(patternOf(pattern)).matches(String.valueOf(text)); }

    private static Object function(String name, List<Object> args) {
        return switch (name) {
            case "regexSearch" -> regexSearch(args.get(0), args.get(1));
            case "regexFullMatch" -> regexFullMatch(args.get(0), args.get(1));
            case "tokenize" -> List.of(String.valueOf(args.get(1)).split(java.util.regex.Pattern.quote(String.valueOf(args.get(0)))));
            case "last" -> { List<Object> values = iterableOf(args.get(0)); yield values.isEmpty() ? "" : values.get(values.size() - 1); }
            case "size" -> (long) iterableOf(args.get(0)).size();
            case "distinct" -> {
                List<Object> unique = new ArrayList<>();
                List<String> keys = new ArrayList<>();
                for (Object value : iterableOf(args.get(0))) {
                    String key = String.valueOf(value);
                    if (value != null && !keys.contains(key)) { keys.add(key); unique.add(value); }
                }
                yield unique;
            }
            case "join" -> {
                StringBuilder joined = new StringBuilder();
                List<Object> parts = iterableOf(args.get(1));
                for (int i = 0; i < parts.size(); i++) { if (i > 0) joined.append(String.valueOf(args.get(0))); joined.append(String.valueOf(parts.get(i))); }
                yield joined.toString();
            }
            case "strip" -> {
                String text = String.valueOf(args.get(0));
                if (args.size() < 2) { yield text.trim(); }
                String chars = String.valueOf(args.get(1));
                int start = 0, end = text.length();
                while (start < end && chars.indexOf(text.charAt(start)) >= 0) start++;
                while (end > start && chars.indexOf(text.charAt(end - 1)) >= 0) end--;
                yield text.substring(start, end);
            }
            case "urlHost" -> {
                try { yield new java.net.URI(String.valueOf(args.get(0))).getHost(); }
                catch (java.net.URISyntaxException | NullPointerException e) { yield null; }
            }
            case "parseInt" -> {
                try { yield Long.parseLong(String.valueOf(args.get(0)).trim()); }
                catch (NumberFormatException e) { yield args.size() > 1 ? args.get(1) : -1L; }
            }
            case "truthy" -> truthy(args.get(0));
            case "pathSegments" -> {
                List<Object> segments = new ArrayList<>();
                for (String segment : String.valueOf(args.get(0)).split("/")) {
                    if (!segment.isEmpty() && !segment.startsWith("{")) segments.add(segment);
                }
                yield segments;
            }
            case "enumerate" -> {
                List<Object> source = iterableOf(args.get(0));
                List<Object> pairs = new ArrayList<>();
                for (int i = 0; i < source.size(); i++) {
                    List<Object> pair = new ArrayList<>();
                    pair.add((long) i);
                    pair.add(source.get(i));
                    pairs.add(pair);
                }
                yield pairs;
            }
            case "type" -> {
                Object value = args.get(0);
                if (value == null) yield "NoneType";
                if (value instanceof String) yield "string";
                if (value instanceof Boolean) yield "bool";
                if (value instanceof Double || value instanceof Float || value instanceof java.math.BigDecimal) yield "float";
                if (value instanceof Number) yield "int";
                if (value instanceof Map<?, ?>) yield "dict";
                if (value instanceof Iterable<?>) yield "list";
                yield "object";
            }
            case "occurrence" -> new Diagnostic(string(args, 0), string(args, 1), string(args, 2));
            case "operationMessage" -> operationMessage(args.get(0));
            default -> throw new IllegalArgumentException("unknown function: " + name);
        };
    }

    private static String operationMessage(Object value) {
        if (!(value instanceof Map<?, ?> parameters)) throw new IllegalArgumentException("operationMessage expects parameters");
        if (Objects.equals(parameters.get("expected"), "safe")) return "GET operation appears to mutate state";
        if (Objects.equals(parameters.get("match"), "full-resource-replacement")) return "POST appears to replace an identified resource";
        if (Objects.equals(parameters.get("match"), "partial-update")) return "PUT appears to perform a partial update";
        if (Objects.equals(parameters.get("match"), "inconsistent-method-resource-semantics")) return "HTTP method and resource semantics appear inconsistent";
        return "Supported operation semantics are unclear";
    }

    private static String string(List<Object> args, int index) { return Objects.toString(args.get(index), null); }

    private enum Kind { ID, STRING, NUMBER, REGEX, SYMBOL, EOF }
    private record Token(Kind kind, String text) { }

    private static final class Lexer {
        private final String source; private int cursor; private Token previous;
        Lexer(String source) { this.source = source; }
        Token next() { return previous = scan(); }

        private Token scan() {
            while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) cursor++;
            if (cursor == source.length()) return new Token(Kind.EOF, "");
            char ch = source.charAt(cursor);
            if (Character.isJavaIdentifierStart(ch)) { int start = cursor++; while (cursor < source.length() && Character.isJavaIdentifierPart(source.charAt(cursor))) cursor++; return new Token(Kind.ID, source.substring(start, cursor)); }
            if (ch == '"') { StringBuilder out = new StringBuilder(); cursor++; while (cursor < source.length() && source.charAt(cursor) != '"') { if (source.charAt(cursor) == '\\') cursor++; out.append(source.charAt(cursor++)); } if (cursor == source.length()) throw error("unterminated string"); cursor++; return new Token(Kind.STRING, out.toString()); }
            if (Character.isDigit(ch)) { int start = cursor++; while (cursor < source.length() && Character.isDigit(source.charAt(cursor))) cursor++; return new Token(Kind.NUMBER, source.substring(start, cursor)); }
            // ~/pattern/ (explicit) or /pattern/ where an operand is expected.
            boolean tildeSlash = ch == '~' && cursor + 1 < source.length() && source.charAt(cursor + 1) == '/';
            if (tildeSlash || (ch == '/' && regexExpected())) {
                cursor += tildeSlash ? 2 : 1;
                StringBuilder out = new StringBuilder();
                while (cursor < source.length() && source.charAt(cursor) != '/') {
                    char c = source.charAt(cursor);
                    if (c == '\\' && cursor + 1 < source.length()) {
                        char escaped = source.charAt(cursor + 1);
                        out.append(escaped == '/' ? "/" : "\\" + escaped);
                        cursor += 2;
                        continue;
                    }
                    out.append(c); cursor++;
                }
                if (cursor == source.length()) throw error("unterminated regex");
                cursor++; return new Token(Kind.REGEX, out.toString());
            }
            for (String operator : List.of("==~", "=~", "==", "!=", "<=", ">=", "&&", "||", "->")) if (source.startsWith(operator, cursor)) { cursor += operator.length(); return new Token(Kind.SYMBOL, operator); }
            cursor++; return new Token(Kind.SYMBOL, String.valueOf(ch));
        }

        /** True when the next token sits where an operand (hence a regex) is expected. */
        private boolean regexExpected() {
            if (previous == null) return true;
            if (previous.kind() == Kind.ID) return previous.text().equals("return");
            if (previous.kind() == Kind.SYMBOL) return switch (previous.text()) {
                case ")", "]", "}" -> false;
                default -> true;
            };
            return false;
        }

        private IllegalArgumentException error(String message) { return new IllegalArgumentException(message + " at " + cursor); }
    }

    private static final class Parser {
        private final Lexer lexer; private Token token; private Token lookahead;
        Parser(String source) { lexer = new Lexer(source); token = lexer.next(); lookahead = lexer.next(); }
        Program parse() { expect("distill"); expect("("); String api = expectId(); expect(","); String rule = expectId(); expect(")"); expect("{"); expect("return"); Expr expression = expression(); expect(";"); expect("}"); expectKind(Kind.EOF); return new Program(expression); }
        private Expr expression() { Expr condition = or(); if (accept("?")) { Expr whenTrue = expression(); expect(":"); Expr whenFalse = expression(); return env -> truthy(condition.eval(env)) ? whenTrue.eval(env) : whenFalse.eval(env); } return condition; }
        private Expr or() { Expr left = and(); while (accept("||")) { Expr right = and(); Expr l = left, r = right; left = env -> truthy(l.eval(env)) || truthy(r.eval(env)); } return left; }
        private Expr and() { Expr left = equality(); while (accept("&&")) { Expr right = equality(); Expr l = left, r = right; left = env -> truthy(l.eval(env)) && truthy(r.eval(env)); } return left; }
        private Expr equality() {
            Expr left = relational();
            while (at("==") || at("!=") || at("==~") || at("=~") || at("is")) {
                if (accept("is")) {
                    expect("blank");
                    Expr value = left;
                    left = env -> isBlank(value.eval(env));
                } else {
                    String op = token.text();
                    advance();
                    Expr right = relational();
                    left = binary(left, right, op);
                }
            }
            return left;
        }
        private Expr relational() { Expr left = additive(); while (at("<") || at("<=") || at(">") || at(">=")) { String op = token.text(); advance(); Expr right = additive(); left = binary(left, right, op); } return left; }
        private Expr additive() { Expr left = unary(); while (accept("+")) { Expr right = unary(); Expr prior = left; left = env -> { Object a = prior.eval(env), b = right.eval(env); if (a instanceof Number x && b instanceof Number y) return x.longValue() + y.longValue(); if (a instanceof Iterable<?> && b instanceof Iterable<?>) { List<Object> merged = new ArrayList<>(iterableOf(a)); merged.addAll(iterableOf(b)); return merged; } return Objects.toString(a, "null") + Objects.toString(b, "null"); }; } return left; }
        private Expr unary() {
            if (accept("!")) { Expr value = unary(); return env -> !truthy(value.eval(env)); }
            if (accept("-")) { Expr value = unary(); return env -> { Object v = value.eval(env); return v instanceof Number n ? -n.longValue() : v; }; }
            return postfix(primary());
        }
        private Expr primary() {
            if (accept("(")) { Expr value = expression(); expect(")"); return value; }
            if (accept("[")) {
                List<Expr> elements = new ArrayList<>();
                if (!accept("]")) { do elements.add(expression()); while (accept(",")); expect("]"); }
                List<Expr> els = elements;
                return env -> { List<Object> list = new ArrayList<>(); for (Expr e : els) list.add(e.eval(env)); return list; };
            }
            if (token.kind() == Kind.STRING) { String value = token.text(); advance(); return env -> value; }
            if (token.kind() == Kind.REGEX) { Regex value = new Regex(token.text()); advance(); return env -> value; }
            if (token.kind() == Kind.NUMBER) { long value = Long.parseLong(token.text()); advance(); return env -> value; }
            if (token.kind() == Kind.ID && (token.text().equals("true") || token.text().equals("false"))) { boolean value = token.text().equals("true"); advance(); return env -> value; }
            String name = expectId(); if (accept("(")) {
                if (!KNOWN_FUNCTIONS.contains(name)) throw new IllegalArgumentException("unknown function: " + name);
                List<Expr> args = arguments();
                return env -> function(name, args.stream().map(a -> a.eval(env)).toList());
            }
            return env -> env.get(name);
        }
        private Expr postfix(Expr value) {
            while (at(".") || (at("?") && ".".equals(lookahead.text())) || at("[")) {
                if (accept(".")) {
                    String name = expectId();
                    if (!KNOWN_MEMBERS.contains(name)) throw new IllegalArgumentException("unknown property or operation: " + name);
                    if (accept("(")) { List<Expr> args = arguments(); Expr receiver = value; value = env -> call(receiver.eval(env), name, args.stream().map(a -> a.eval(env)).toList()); }
                    else if (at("{")) { ParsedClosure closure = closure(); Expr receiver = value; value = env -> call(receiver.eval(env), name, List.of((Closure) argument -> closure.body().eval(with(env, closure.parameter(), argument)))); }
                    else { Expr receiver = value; value = env -> member(receiver.eval(env), name); }
                } else if (accept("?")) {
                    expect(".");
                    String name = expectId();
                    if (!KNOWN_MEMBERS.contains(name)) throw new IllegalArgumentException("unknown property or operation: " + name);
                    Expr receiver = value;
                    if (accept("(")) {
                        List<Expr> args = arguments();
                        value = env -> {
                            Object target = receiver.eval(env);
                            return target == null ? null : call(target, name, args.stream().map(a -> a.eval(env)).toList());
                        };
                    } else if (at("{")) {
                        ParsedClosure closure = closure();
                        value = env -> {
                            Object target = receiver.eval(env);
                            return target == null ? null : call(target, name,
                                    List.of((Closure) argument -> closure.body().eval(with(env, closure.parameter(), argument))));
                        };
                    } else {
                        value = env -> member(receiver.eval(env), name);
                    }
                } else {
                    expect("["); Expr key = expression(); expect("]"); Expr receiver = value;
                    value = env -> index(receiver.eval(env), key.eval(env));
                }
            }
            return value;
        }
        private ParsedClosure closure() { expect("{"); String parameter = expectId(); expect("->"); Expr body = expression(); expect("}"); return new ParsedClosure(parameter, body); }
        private List<Expr> arguments() { List<Expr> result = new ArrayList<>(); if (!accept(")")) { do result.add(expression()); while (accept(",")); expect(")"); } return result; }
        private Expr binary(Expr left, Expr right, String op) { return env -> { Object a = left.eval(env), b = right.eval(env); return switch (op) { case "==" -> equalValue(a, b); case "!=" -> !equalValue(a, b); case "==~" -> regexFullMatch(b, a); case "=~" -> regexSearch(b, a); case "<" -> compare(a, b) < 0; case "<=" -> compare(a, b) <= 0; case ">" -> compare(a, b) > 0; case ">=" -> compare(a, b) >= 0; case "&&" -> truthy(a) && truthy(b); case "||" -> truthy(a) || truthy(b); default -> throw new IllegalStateException(op); }; }; }
        private static Map<String, Object> with(Map<String, Object> env, String key, Object value) { Map<String, Object> result = new LinkedHashMap<>(env); result.put(key, value); return result; }
        /** True when the current token is the keyword/operator {@code text} — never a string or regex literal that happens to read the same. */
        private boolean at(String text) { return token.kind() != Kind.STRING && token.kind() != Kind.REGEX && token.text().equals(text); }
        private boolean accept(String text) { if (at(text)) { advance(); return true; } return false; }
        private void expect(String text) { if (!accept(text)) throw new IllegalArgumentException("expected '" + text + "', got '" + token.text() + "'"); }
        private String expectId() { expectKind(Kind.ID); String result = token.text(); advance(); return result; }
        private void expectKind(Kind kind) { if (token.kind() != kind) throw new IllegalArgumentException("expected " + kind + ", got " + token.text()); }
        private void advance() { token = lookahead; lookahead = lexer.next(); }
    }

    private static final Set<String> KNOWN_FUNCTIONS = Set.of(
            "regexSearch", "regexFullMatch", "tokenize", "last", "size", "distinct", "join", "strip",
            "urlHost", "parseInt", "truthy", "pathSegments", "enumerate", "type", "occurrence",
            "operationMessage");

    private static final Set<String> KNOWN_MEMBERS = Set.of(
            "all", "allowed", "any", "array", "audience", "case", "check", "compositionKind", "contains",
            "count", "description", "distinct", "endsWith", "enumPresent", "enumValues", "example",
            "examplePresent", "exampleStrings", "exclusiveMaximum", "exclusiveMinimum", "expand", "expected",
            "explode", "extensibleEnum", "extensionKeys", "filter", "find", "forbidden", "format", "group",
            "headerDetails", "headers", "in", "info", "inlineCompositionMembers", "keys", "length", "lint",
            "location", "lower", "map", "match", "maxItems", "maxLength", "maximum", "mediaTypes", "method",
            "methods", "minLength", "minimum", "name", "nullable", "numericStatusKeys", "openapiVersion",
            "operationDetails", "operationId", "operations", "parameters", "parserMessages", "path", "paths",
            "pattern", "pointer", "properties", "requestBodyInlineObject", "requestBodyPresent",
            "requestBodyRequired", "requestMediaTypes", "require", "required", "requiredFields", "responses",
            "schemaInlineObject", "schemaMaximum", "schemaPresent", "schemaType", "schemaTypes", "schemas",
            "scope", "security", "segments", "servers", "startsWith", "status", "style", "suffix", "summary",
            "tags", "templateParameters", "title", "toList", "trim", "type", "values");
}
