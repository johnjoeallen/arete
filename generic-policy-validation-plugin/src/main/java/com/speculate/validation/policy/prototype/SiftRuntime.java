package com.speculate.validation.policy;

import com.google.re2j.Pattern;
import com.speculate.validation.policy.Occurrence;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/** Interpreter for Sift, the Java-shaped fluent detector language (Detector.sift). */
public final class SiftRuntime {
    public List<Occurrence> execute(String source, Map<String, Object> api, Map<String, Object> rule) {
        return new Parser(source).parse().apply(Map.of("api", api, "rule", rule));
    }

    private interface Expr { Object eval(Map<String, Object> env); }
    private interface Closure { Object apply(Object value); }
    private record Program(Expr result) { List<Occurrence> apply(Map<String, Object> env) { return castOccurrences(result.eval(env)); } }
    private record ParsedClosure(String parameter, Expr body) { }

    private static List<Occurrence> castOccurrences(Object value) {
        if (!(value instanceof Iterable<?> values)) throw new IllegalArgumentException("detector must return a collection");
        List<Occurrence> result = new ArrayList<>();
        for (Object item : values) {
            if (!(item instanceof Occurrence occurrence)) throw new IllegalArgumentException("detector returned a non-occurrence");
            result.add(occurrence);
        }
        return result;
    }

    private static Object member(Object value, String name) {
        if (value == null) return null;
        if (value instanceof Map<?, ?> map) return map.get(name);
        if (value instanceof String text) return switch (name) {
            case "length" -> text.length();
            default -> null;
        };
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

    private static Object function(String name, List<Object> args) {
        return switch (name) {
            case "regexSearch" -> Pattern.compile(String.valueOf(args.get(0))).matcher(String.valueOf(args.get(1))).find();
            case "regexFullMatch" -> Pattern.compile(String.valueOf(args.get(0))).matches(String.valueOf(args.get(1)));
            case "tokenize" -> List.of(String.valueOf(args.get(1)).split(java.util.regex.Pattern.quote(String.valueOf(args.get(0)))));
            case "last" -> { List<Object> values = iterableOf(args.get(0)); yield values.isEmpty() ? "" : values.get(values.size() - 1); }
            case "occurrence" -> new Occurrence(string(args, 0), string(args, 1), string(args, 2));
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

    private enum Kind { ID, STRING, NUMBER, SYMBOL, EOF }
    private record Token(Kind kind, String text) { }

    private static final class Lexer {
        private final String source; private int cursor;
        Lexer(String source) { this.source = source; }
        Token next() {
            while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) cursor++;
            if (cursor == source.length()) return new Token(Kind.EOF, "");
            char ch = source.charAt(cursor);
            if (Character.isJavaIdentifierStart(ch)) { int start = cursor++; while (cursor < source.length() && Character.isJavaIdentifierPart(source.charAt(cursor))) cursor++; return new Token(Kind.ID, source.substring(start, cursor)); }
            if (ch == '"') { StringBuilder out = new StringBuilder(); cursor++; while (cursor < source.length() && source.charAt(cursor) != '"') { if (source.charAt(cursor) == '\\') cursor++; out.append(source.charAt(cursor++)); } if (cursor == source.length()) throw error("unterminated string"); cursor++; return new Token(Kind.STRING, out.toString()); }
            if (Character.isDigit(ch)) { int start = cursor++; while (cursor < source.length() && Character.isDigit(source.charAt(cursor))) cursor++; return new Token(Kind.NUMBER, source.substring(start, cursor)); }
            for (String operator : List.of("==", "!=", "&&", "||", "->")) if (source.startsWith(operator, cursor)) { cursor += operator.length(); return new Token(Kind.SYMBOL, operator); }
            cursor++; return new Token(Kind.SYMBOL, String.valueOf(ch));
        }
        private IllegalArgumentException error(String message) { return new IllegalArgumentException(message + " at " + cursor); }
    }

    private static final class Parser {
        private final Lexer lexer; private Token token;
        Parser(String source) { lexer = new Lexer(source); token = lexer.next(); }
        Program parse() { expect("sift"); expect("("); String api = expectId(); expect(","); String rule = expectId(); expect(")"); expect("{"); expect("return"); Expr expression = expression(); expect(";"); expect("}"); expectKind(Kind.EOF); return new Program(expression); }
        private Expr expression() { Expr condition = or(); if (accept("?")) { Expr whenTrue = expression(); expect(":"); Expr whenFalse = expression(); return env -> truthy(condition.eval(env)) ? whenTrue.eval(env) : whenFalse.eval(env); } return condition; }
        private Expr or() { Expr left = and(); while (accept("||")) { Expr right = and(); left = binary(left, right, "||"); } return left; }
        private Expr and() { Expr left = equality(); while (accept("&&")) { Expr right = equality(); left = binary(left, right, "&&"); } return left; }
        private Expr equality() { Expr left = additive(); while (token.text().equals("==") || token.text().equals("!=")) { String op = token.text(); advance(); Expr right = additive(); left = binary(left, right, op); } return left; }
        private Expr additive() { Expr left = unary(); while (accept("+")) { Expr right = unary(); Expr prior = left; left = env -> { Object a = prior.eval(env), b = right.eval(env); if (a instanceof Number x && b instanceof Number y) return x.longValue() + y.longValue(); return Objects.toString(a, "null") + Objects.toString(b, "null"); }; } return left; }
        private Expr unary() { if (accept("!")) { Expr value = unary(); return env -> !truthy(value.eval(env)); } return postfix(primary()); }
        private Expr primary() {
            if (accept("(")) { Expr value = expression(); expect(")"); return value; }
            if (token.kind() == Kind.STRING) { String value = token.text(); advance(); return env -> value; }
            if (token.kind() == Kind.NUMBER) { long value = Long.parseLong(token.text()); advance(); return env -> value; }
            String name = expectId(); if (accept("(")) { List<Expr> args = arguments(); return env -> function(name, args.stream().map(a -> a.eval(env)).toList()); }
            return env -> env.get(name);
        }
        private Expr postfix(Expr value) {
            while (accept(".")) { String name = expectId(); if (accept("(")) { List<Expr> args = arguments(); Expr receiver = value; value = env -> call(receiver.eval(env), name, args.stream().map(a -> a.eval(env)).toList()); } else if (token.text().equals("{")) { ParsedClosure closure = closure(); Expr receiver = value; value = env -> call(receiver.eval(env), name, List.of((Closure) argument -> closure.body().eval(with(env, closure.parameter(), argument)))); } else { Expr receiver = value; value = env -> member(receiver.eval(env), name); } }
            return value;
        }
        private ParsedClosure closure() { expect("{"); String parameter = expectId(); expect("->"); Expr body = expression(); expect("}"); return new ParsedClosure(parameter, body); }
        private List<Expr> arguments() { List<Expr> result = new ArrayList<>(); if (!accept(")")) { do result.add(expression()); while (accept(",")); expect(")"); } return result; }
        private Expr binary(Expr left, Expr right, String op) { return env -> { Object a = left.eval(env), b = right.eval(env); return switch (op) { case "==" -> Objects.equals(a, b); case "!=" -> !Objects.equals(a, b); case "&&" -> truthy(a) && truthy(b); case "||" -> truthy(a) || truthy(b); default -> throw new IllegalStateException(op); }; }; }
        private static Map<String, Object> with(Map<String, Object> env, String key, Object value) { Map<String, Object> result = new LinkedHashMap<>(env); result.put(key, value); return result; }
        private boolean accept(String text) { if (token.text().equals(text)) { advance(); return true; } return false; }
        private void expect(String text) { if (!accept(text)) throw new IllegalArgumentException("expected '" + text + "', got '" + token.text() + "'"); }
        private String expectId() { expectKind(Kind.ID); String result = token.text(); advance(); return result; }
        private void expectKind(Kind kind) { if (token.kind() != kind) throw new IllegalArgumentException("expected " + kind + ", got " + token.text()); }
        private void advance() { token = lexer.next(); }
    }
}
