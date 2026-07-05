package org.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JSONTokener {
    private final String text;
    private int index = 0;

    public JSONTokener(String text) {
        this.text = text == null ? "" : text;
    }

    public Object nextValue() throws JSONException {
        skipWhitespace();
        Object value = parseValue();
        skipWhitespace();
        return value;
    }

    public char nextClean() {
        skipWhitespace();
        if (index >= text.length()) {
            return 0;
        }
        return text.charAt(index++);
    }

    private Object parseValue() throws JSONException {
        skipWhitespace();
        if (index >= text.length()) {
            throw error("Unexpected end of JSON.");
        }

        char current = text.charAt(index);
        if (current == '{') {
            return parseObject();
        }
        if (current == '[') {
            return parseArray();
        }
        if (current == '"') {
            return parseString();
        }
        if (startsWith("true")) {
            index += 4;
            return Boolean.TRUE;
        }
        if (startsWith("false")) {
            index += 5;
            return Boolean.FALSE;
        }
        if (startsWith("null")) {
            index += 4;
            return JSONObject.NULL;
        }
        if (current == '-' || isDigit(current)) {
            return parseNumber();
        }
        throw error("Unsupported JSON value.");
    }

    private JSONObject parseObject() throws JSONException {
        expect('{');
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        skipWhitespace();
        if (peek('}')) {
            index++;
            return new JSONObject(map);
        }

        while (true) {
            skipWhitespace();
            if (!peek('"')) {
                throw error("Expected object key.");
            }
            String key = parseString();
            skipWhitespace();
            expect(':');
            map.put(key, parseValue());
            skipWhitespace();
            if (peek('}')) {
                index++;
                return new JSONObject(map);
            }
            expect(',');
        }
    }

    private JSONArray parseArray() throws JSONException {
        expect('[');
        List<Object> values = new ArrayList<Object>();
        skipWhitespace();
        if (peek(']')) {
            index++;
            return new JSONArray(values);
        }

        while (true) {
            values.add(parseValue());
            skipWhitespace();
            if (peek(']')) {
                index++;
                return new JSONArray(values);
            }
            expect(',');
        }
    }

    private String parseString() throws JSONException {
        expect('"');
        StringBuilder builder = new StringBuilder();
        while (index < text.length()) {
            char current = text.charAt(index++);
            if (current == '"') {
                return builder.toString();
            }
            if (current != '\\') {
                builder.append(current);
                continue;
            }
            if (index >= text.length()) {
                throw error("Unfinished escape sequence.");
            }
            char escaped = text.charAt(index++);
            switch (escaped) {
                case '"':
                case '\\':
                case '/':
                    builder.append(escaped);
                    break;
                case 'b':
                    builder.append('\b');
                    break;
                case 'f':
                    builder.append('\f');
                    break;
                case 'n':
                    builder.append('\n');
                    break;
                case 'r':
                    builder.append('\r');
                    break;
                case 't':
                    builder.append('\t');
                    break;
                case 'u':
                    builder.append(parseUnicodeEscape());
                    break;
                default:
                    throw error("Unsupported escape sequence.");
            }
        }
        throw error("Unterminated JSON string.");
    }

    private char parseUnicodeEscape() throws JSONException {
        if (index + 4 > text.length()) {
            throw error("Short unicode escape.");
        }
        int value = 0;
        for (int count = 0; count < 4; count++) {
            int digit = Character.digit(text.charAt(index++), 16);
            if (digit < 0) {
                throw error("Bad unicode escape.");
            }
            value = value * 16 + digit;
        }
        return (char) value;
    }

    private Number parseNumber() throws JSONException {
        int start = index;
        if (peek('-')) {
            index++;
        }
        if (index >= text.length() || !isDigit(text.charAt(index))) {
            throw error("Bad JSON number.");
        }
        while (index < text.length() && isDigit(text.charAt(index))) {
            index++;
        }
        boolean decimal = false;
        if (peek('.')) {
            decimal = true;
            index++;
            if (index >= text.length() || !isDigit(text.charAt(index))) {
                throw error("Bad JSON fraction.");
            }
            while (index < text.length() && isDigit(text.charAt(index))) {
                index++;
            }
        }
        String numberText = text.substring(start, index);
        try {
            return decimal ? Double.valueOf(numberText) : Integer.valueOf(numberText);
        } catch (NumberFormatException ignored) {
            try {
                return Long.valueOf(numberText);
            } catch (NumberFormatException error) {
                throw error("Bad JSON number.");
            }
        }
    }

    private boolean startsWith(String value) {
        return text.startsWith(value, index);
    }

    private boolean peek(char value) {
        return index < text.length() && text.charAt(index) == value;
    }

    private void expect(char value) throws JSONException {
        if (!peek(value)) {
            throw error("Expected '" + value + "'.");
        }
        index++;
    }

    private void skipWhitespace() {
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current != ' ' && current != '\n' && current != '\r' && current != '\t') {
                return;
            }
            index++;
        }
    }

    private JSONException error(String message) {
        return new JSONException(message + " Offset " + index + ".");
    }

    private static boolean isDigit(char value) {
        return value >= '0' && value <= '9';
    }

    static void appendJsonValue(StringBuilder builder, Object value) {
        if (value == null || value == JSONObject.NULL) {
            builder.append("null");
        } else if (value instanceof String) {
            appendJsonString(builder, (String) value);
        } else if (value instanceof Number || value instanceof Boolean) {
            builder.append(String.valueOf(value));
        } else {
            builder.append(value.toString());
        }
    }

    static void appendJsonString(StringBuilder builder, String value) {
        String text = value == null ? "" : value;
        builder.append('"');
        for (int i = 0; i < text.length(); i++) {
            char current = text.charAt(i);
            switch (current) {
                case '"':
                    builder.append("\\\"");
                    break;
                case '\\':
                    builder.append("\\\\");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                default:
                    builder.append(current);
                    break;
            }
        }
        builder.append('"');
    }
}
