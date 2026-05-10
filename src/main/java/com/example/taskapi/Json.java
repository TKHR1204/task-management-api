package com.example.taskapi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public final class Json {
    private Json() {
    }

    public static String task(Task task) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", task.id());
        values.put("title", task.title());
        values.put("description", task.description());
        values.put("completed", task.completed());
        values.put("dueDate", task.dueDate() == null ? null : task.dueDate().toString());
        values.put("priority", task.priority().name());
        values.put("tags", task.tags());
        values.put("createdAt", task.createdAt().toString());
        values.put("updatedAt", task.updatedAt().toString());
        return object(values);
    }

    public static String tasks(Collection<Task> tasks) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (Task task : tasks) {
            joiner.add(task(task));
        }
        return joiner.toString();
    }

    public static String stats(TaskStats stats) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("total", stats.total());
        values.put("open", stats.open());
        values.put("completed", stats.completed());
        values.put("overdue", stats.overdue());
        values.put("highPriority", stats.highPriority());
        return object(values);
    }

    public static String error(String message) {
        return object(Map.of("error", message));
    }

    public static String object(Map<String, ?> values) {
        StringJoiner joiner = new StringJoiner(",", "{", "}");
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            joiner.add(quote(entry.getKey()) + ":" + value(entry.getValue()));
        }
        return joiner.toString();
    }

    public static Map<String, Object> parseObject(String json) {
        Parser parser = new Parser(json);
        return parser.parseObject();
    }

    private static String value(Object value) {
        return switch (value) {
            case null -> "null";
            case String text -> quote(text);
            case Number number -> number.toString();
            case Boolean bool -> bool.toString();
            case Map<?, ?> map -> objectFromAnyMap(map);
            case Collection<?> collection -> array(collection);
            default -> quote(value.toString());
        };
    }

    private static String objectFromAnyMap(Map<?, ?> values) {
        StringJoiner joiner = new StringJoiner(",", "{", "}");
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            joiner.add(quote(String.valueOf(entry.getKey())) + ":" + value(entry.getValue()));
        }
        return joiner.toString();
    }

    private static String array(Collection<?> values) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (Object item : values) {
            joiner.add(value(item));
        }
        return joiner.toString();
    }

    private static String quote(String value) {
        StringBuilder builder = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            switch (current) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> builder.append(current);
            }
        }
        return builder.append('"').toString();
    }

    private static final class Parser {
        private final String json;
        private int index;

        private Parser(String json) {
            this.json = json == null ? "" : json;
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> values = parseObjectBody();
            skipWhitespace();
            if (index != json.length()) {
                throw badRequest("Unexpected content after JSON object.");
            }
            return values;
        }

        private Map<String, Object> parseObjectBody() {
            skipWhitespace();
            expect('{');
            Map<String, Object> values = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                index++;
                return values;
            }

            while (index < json.length()) {
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                values.put(key, parseValue());
                skipWhitespace();

                if (peek(',')) {
                    index++;
                    skipWhitespace();
                    continue;
                }

                expect('}');
                return values;
            }

            throw badRequest("Unclosed JSON object.");
        }

        private Object parseValue() {
            if (peek('"')) {
                return parseString();
            }
            if (peek('[')) {
                return parseArray();
            }
            if (match("true")) {
                return true;
            }
            if (match("false")) {
                return false;
            }
            if (match("null")) {
                return null;
            }
            throw badRequest("Only string, boolean, null, and array values are supported.");
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> values = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                index++;
                return values;
            }

            while (index < json.length()) {
                values.add(parseValue());
                skipWhitespace();

                if (peek(',')) {
                    index++;
                    skipWhitespace();
                    continue;
                }

                expect(']');
                return values;
            }

            throw badRequest("Unclosed array value.");
        }

        private String parseString() {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (index < json.length()) {
                char current = json.charAt(index++);
                if (current == '"') {
                    return builder.toString();
                }
                if (current == '\\') {
                    builder.append(parseEscape());
                } else {
                    builder.append(current);
                }
            }
            throw badRequest("Unclosed string value.");
        }

        private char parseEscape() {
            if (index >= json.length()) {
                throw badRequest("Invalid escape sequence.");
            }
            char escaped = json.charAt(index++);
            return switch (escaped) {
                case '"' -> '"';
                case '\\' -> '\\';
                case '/' -> '/';
                case 'b' -> '\b';
                case 'f' -> '\f';
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case 'u' -> parseUnicodeEscape();
                default -> throw badRequest("Unsupported escape sequence.");
            };
        }

        private char parseUnicodeEscape() {
            if (index + 4 > json.length()) {
                throw badRequest("Invalid unicode escape sequence.");
            }

            String hex = json.substring(index, index + 4);
            index += 4;
            try {
                return (char) Integer.parseInt(hex, 16);
            } catch (NumberFormatException e) {
                throw badRequest("Invalid unicode escape sequence.");
            }
        }

        private boolean match(String literal) {
            if (json.startsWith(literal, index)) {
                index += literal.length();
                return true;
            }
            return false;
        }

        private boolean peek(char expected) {
            return index < json.length() && json.charAt(index) == expected;
        }

        private void expect(char expected) {
            if (!peek(expected)) {
                throw badRequest("Invalid JSON body.");
            }
            index++;
        }

        private void skipWhitespace() {
            while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
                index++;
            }
        }

        private ApiException badRequest(String message) {
            return new ApiException(400, message);
        }
    }
}
