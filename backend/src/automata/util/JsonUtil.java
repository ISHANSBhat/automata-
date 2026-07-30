package automata.util;

import java.util.*;

/**
 * Zero-dependency, hand-rolled JSON parser and serializer.
 * Handles the fixed set of shapes used by the Automata Maker API:
 * nested objects, arrays, strings, booleans, numbers, and null.
 *
 * <p>This is intentionally minimal — it covers exactly what our three
 * API endpoints need and nothing more.</p>
 */
public final class JsonUtil {

    private JsonUtil() {}

    // =========================================================================
    // Serialization (Java → JSON string)
    // =========================================================================

    /** Escapes a Java string for safe embedding inside a JSON string literal. */
    public static String escape(String s) {
        if (s == null) return "";
        var sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default   -> {
                    if (c < 0x20) {
                        sb.append("\\u%04x".formatted((int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /** Wraps a value in JSON double-quotes with proper escaping. */
    public static String quoted(String s) {
        return "\"" + escape(s) + "\"";
    }

    /** Serializes a list of already-serialized JSON fragments into a JSON array. */
    public static String array(List<String> jsonFragments) {
        return "[" + String.join(",", jsonFragments) + "]";
    }

    /** Serializes a map of key → already-serialized-JSON-value into a JSON object. */
    public static String object(Map<String, String> entries) {
        var sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : entries.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append(quoted(entry.getKey()));
            sb.append(":");
            sb.append(entry.getValue());
        }
        sb.append("}");
        return sb.toString();
    }

    /** Convenience: build an object from interleaved key, value, key, value, ... */
    public static String objectOf(String... kvPairs) {
        if (kvPairs.length % 2 != 0) {
            throw new IllegalArgumentException("objectOf requires an even number of arguments");
        }
        var map = new LinkedHashMap<String, String>();
        for (int i = 0; i < kvPairs.length; i += 2) {
            map.put(kvPairs[i], kvPairs[i + 1]);
        }
        return object(map);
    }

    // =========================================================================
    // Deserialization (JSON string → Java objects)
    // =========================================================================

    /**
     * Parses a JSON string into a Java object tree.
     * Returns one of: {@code Map<String,Object>}, {@code List<Object>},
     * {@code String}, {@code Boolean}, {@code Double}, or {@code null}.
     */
    public static Object parse(String json) {
        if (json == null || json.isBlank()) {
            throw new JsonParseException("Empty JSON input");
        }
        var parser = new Parser(json.trim());
        Object result = parser.parseValue();
        parser.skipWhitespace();
        if (parser.hasMore()) {
            throw new JsonParseException("Unexpected trailing content at position " + parser.pos);
        }
        return result;
    }

    /** Convenience: parse and cast to Map. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        Object result = parse(json);
        if (result instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new JsonParseException("Expected JSON object, got: " + result.getClass().getSimpleName());
    }

    /** Convenience: parse and cast to List. */
    @SuppressWarnings("unchecked")
    public static List<Object> parseArray(String json) {
        Object result = parse(json);
        if (result instanceof List<?> list) {
            return (List<Object>) list;
        }
        throw new JsonParseException("Expected JSON array, got: " + result.getClass().getSimpleName());
    }

    /** Safely extract a String value from a parsed JSON map. */
    public static String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val == null ? null : val.toString();
    }

    /** Safely extract a boolean from a parsed JSON map (defaults to false). */
    public static boolean getBoolean(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return "true".equalsIgnoreCase(s);
        return false;
    }

    /** Safely extract a List from a parsed JSON map. */
    @SuppressWarnings("unchecked")
    public static List<Object> getArray(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof List<?> list) return (List<Object>) list;
        return List.of();
    }

    /** Safely extract a nested Map from a parsed JSON map. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> getObject(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Map<?, ?> m) return (Map<String, Object>) m;
        return null;
    }

    // =========================================================================
    // Recursive-descent JSON parser
    // =========================================================================

    private static class Parser {
        private final String input;
        int pos = 0;

        Parser(String input) {
            this.input = input;
        }

        boolean hasMore() {
            return pos < input.length();
        }

        char peek() {
            if (!hasMore()) throw new JsonParseException("Unexpected end of input");
            return input.charAt(pos);
        }

        char advance() {
            char c = peek();
            pos++;
            return c;
        }

        void expect(char c) {
            char actual = advance();
            if (actual != c) {
                throw new JsonParseException(
                        "Expected '%c' but got '%c' at position %d".formatted(c, actual, pos - 1));
            }
        }

        void skipWhitespace() {
            while (hasMore() && Character.isWhitespace(input.charAt(pos))) {
                pos++;
            }
        }

        Object parseValue() {
            skipWhitespace();
            if (!hasMore()) throw new JsonParseException("Unexpected end of input");
            char c = peek();
            return switch (c) {
                case '"' -> parseString();
                case '{' -> parseObjectValue();
                case '[' -> parseArrayValue();
                case 't', 'f' -> parseBoolean();
                case 'n' -> parseNull();
                default -> {
                    if (c == '-' || Character.isDigit(c)) {
                        yield parseNumber();
                    }
                    throw new JsonParseException(
                            "Unexpected character '%c' at position %d".formatted(c, pos));
                }
            };
        }

        String parseString() {
            expect('"');
            var sb = new StringBuilder();
            while (hasMore()) {
                char c = advance();
                if (c == '"') return sb.toString();
                if (c == '\\') {
                    if (!hasMore()) throw new JsonParseException("Unexpected end in string escape");
                    char esc = advance();
                    switch (esc) {
                        case '"'  -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/'  -> sb.append('/');
                        case 'n'  -> sb.append('\n');
                        case 'r'  -> sb.append('\r');
                        case 't'  -> sb.append('\t');
                        case 'b'  -> sb.append('\b');
                        case 'f'  -> sb.append('\f');
                        case 'u'  -> {
                            if (pos + 4 > input.length()) {
                                throw new JsonParseException("Incomplete unicode escape");
                            }
                            String hex = input.substring(pos, pos + 4);
                            pos += 4;
                            sb.append((char) Integer.parseInt(hex, 16));
                        }
                        default -> throw new JsonParseException(
                                "Invalid escape '\\%c' at position %d".formatted(esc, pos - 1));
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new JsonParseException("Unterminated string");
        }

        Map<String, Object> parseObjectValue() {
            expect('{');
            var map = new LinkedHashMap<String, Object>();
            skipWhitespace();
            if (hasMore() && peek() == '}') {
                advance();
                return map;
            }
            while (true) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                char c = advance();
                if (c == '}') return map;
                if (c != ',') {
                    throw new JsonParseException(
                            "Expected ',' or '}' but got '%c' at position %d".formatted(c, pos - 1));
                }
            }
        }

        List<Object> parseArrayValue() {
            expect('[');
            var list = new ArrayList<>();
            skipWhitespace();
            if (hasMore() && peek() == ']') {
                advance();
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                char c = advance();
                if (c == ']') return list;
                if (c != ',') {
                    throw new JsonParseException(
                            "Expected ',' or ']' but got '%c' at position %d".formatted(c, pos - 1));
                }
            }
        }

        Boolean parseBoolean() {
            if (input.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (input.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new JsonParseException("Invalid boolean at position " + pos);
        }

        Object parseNull() {
            if (input.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new JsonParseException("Invalid null at position " + pos);
        }

        Number parseNumber() {
            int start = pos;
            if (peek() == '-') advance();
            while (hasMore() && Character.isDigit(peek())) advance();
            if (hasMore() && peek() == '.') {
                advance();
                while (hasMore() && Character.isDigit(peek())) advance();
            }
            if (hasMore() && (peek() == 'e' || peek() == 'E')) {
                advance();
                if (hasMore() && (peek() == '+' || peek() == '-')) advance();
                while (hasMore() && Character.isDigit(peek())) advance();
            }
            String numStr = input.substring(start, pos);
            // Return Integer if possible, otherwise Double
            if (!numStr.contains(".") && !numStr.contains("e") && !numStr.contains("E")) {
                try {
                    return Integer.parseInt(numStr);
                } catch (NumberFormatException e) {
                    try {
                        return Long.parseLong(numStr);
                    } catch (NumberFormatException e2) {
                        // fall through to double
                    }
                }
            }
            return Double.parseDouble(numStr);
        }
    }

    // =========================================================================
    // Exception
    // =========================================================================

    public static class JsonParseException extends RuntimeException {
        public JsonParseException(String message) {
            super(message);
        }
    }
}
