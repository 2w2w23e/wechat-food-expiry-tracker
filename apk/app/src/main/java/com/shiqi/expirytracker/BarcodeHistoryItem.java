package com.shiqi.expirytracker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BarcodeHistoryItem {
    static final int CURRENT_SCHEMA_VERSION = 1;

    String barcode = "";
    String name = "";
    String category = "other";
    String unit = "";
    String notes = "";
    String updatedAt = "";

    BarcodeHistoryItem() {
    }

    BarcodeHistoryItem copy() {
        BarcodeHistoryItem item = new BarcodeHistoryItem();
        item.barcode = barcode;
        item.name = name;
        item.category = category;
        item.unit = unit;
        item.notes = notes;
        item.updatedAt = updatedAt;
        return item;
    }

    boolean isReusableTemplate() {
        return barcode.length() > 0 && name.length() > 0;
    }

    BarcodeHistoryItem normalizedForStorage(String nextUpdatedAt) {
        BarcodeHistoryItem item = copy();
        item.barcode = digitsOnly(item.barcode);
        item.name = cleanText(item.name);
        item.category = fallback(cleanText(item.category), "other");
        item.unit = cleanText(item.unit);
        item.notes = cleanText(item.notes);
        item.updatedAt = fallback(cleanText(nextUpdatedAt), cleanText(item.updatedAt));
        return item;
    }

    static List<BarcodeHistoryItem> upsertConfirmedTemplate(
            List<BarcodeHistoryItem> current,
            BarcodeHistoryItem draft,
            String updatedAt,
            int maxItems
    ) {
        int limit = Math.max(maxItems, 1);
        List<BarcodeHistoryItem> result = new ArrayList<BarcodeHistoryItem>();
        BarcodeHistoryItem confirmed = draft == null ? null : draft.normalizedForStorage(updatedAt);

        if (confirmed != null && confirmed.isReusableTemplate()) {
            result.add(confirmed);
        }

        if (current != null) {
            for (BarcodeHistoryItem item : current) {
                if (result.size() >= limit) {
                    break;
                }

                if (item == null) {
                    continue;
                }

                BarcodeHistoryItem normalized = item.normalizedForStorage(item.updatedAt);
                if (!normalized.isReusableTemplate()) {
                    continue;
                }

                if (confirmed != null && confirmed.barcode.equals(normalized.barcode)) {
                    continue;
                }

                result.add(normalized);
            }
        }

        return result;
    }

    static String serializeList(List<BarcodeHistoryItem> items) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\"schemaVersion\":")
                .append(CURRENT_SCHEMA_VERSION)
                .append(",\"items\":[");

        boolean first = true;
        if (items != null) {
            for (BarcodeHistoryItem item : items) {
                if (item == null) {
                    continue;
                }

                BarcodeHistoryItem normalized = item.normalizedForStorage(item.updatedAt);
                if (!normalized.isReusableTemplate()) {
                    continue;
                }

                if (!first) {
                    builder.append(',');
                }
                first = false;
                normalized.appendJson(builder);
            }
        }

        builder.append("]}");
        return builder.toString();
    }

    static List<BarcodeHistoryItem> parseListOrEmpty(String raw) {
        try {
            return parseList(raw);
        } catch (RuntimeException ignored) {
            return new ArrayList<BarcodeHistoryItem>();
        }
    }

    @SuppressWarnings("unchecked")
    static List<BarcodeHistoryItem> parseList(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.length() == 0) {
            return new ArrayList<BarcodeHistoryItem>();
        }

        Object root = new JsonParser(text).parseValueAndEnd();
        Object itemsRoot = root;

        if (root instanceof Map) {
            itemsRoot = ((Map<String, Object>) root).get("items");
        }

        if (!(itemsRoot instanceof List)) {
            throw new IllegalArgumentException("Barcode history JSON must contain an items array.");
        }

        List<BarcodeHistoryItem> items = new ArrayList<BarcodeHistoryItem>();
        for (Object value : (List<Object>) itemsRoot) {
            if (!(value instanceof Map)) {
                continue;
            }

            BarcodeHistoryItem item = fromMap((Map<String, Object>) value);
            if (item.isReusableTemplate()) {
                items.add(item);
            }
        }
        return items;
    }

    private void appendJson(StringBuilder builder) {
        builder.append('{');
        appendJsonField(builder, "barcode", barcode, true);
        appendJsonField(builder, "name", name, false);
        appendJsonField(builder, "category", category, false);
        appendJsonField(builder, "unit", unit, false);
        appendJsonField(builder, "notes", notes, false);
        appendJsonField(builder, "updatedAt", updatedAt, false);
        builder.append('}');
    }

    private static BarcodeHistoryItem fromMap(Map<String, Object> map) {
        BarcodeHistoryItem item = new BarcodeHistoryItem();
        item.barcode = digitsOnly(asText(map.get("barcode")));
        item.name = cleanText(asText(map.get("name")));
        item.category = fallback(cleanText(asText(map.get("category"))), "other");
        item.unit = cleanText(asText(map.get("unit")));
        item.notes = cleanText(asText(map.get("notes")));
        item.updatedAt = cleanText(asText(map.get("updatedAt")));
        return item;
    }

    private static void appendJsonField(StringBuilder builder, String key, String value, boolean first) {
        if (!first) {
            builder.append(',');
        }
        appendJsonString(builder, key);
        builder.append(':');
        appendJsonString(builder, value);
    }

    private static void appendJsonString(StringBuilder builder, String value) {
        String text = value == null ? "" : value;
        builder.append('"');
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            switch (current) {
                case '"':
                    builder.append("\\\"");
                    break;
                case '\\':
                    builder.append("\\\\");
                    break;
                case '\b':
                    builder.append("\\b");
                    break;
                case '\f':
                    builder.append("\\f");
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
                    if (current < 0x20) {
                        builder.append("\\u");
                        appendFourDigitHex(builder, current);
                    } else {
                        builder.append(current);
                    }
                    break;
            }
        }
        builder.append('"');
    }

    private static void appendFourDigitHex(StringBuilder builder, char value) {
        String hex = Integer.toHexString(value);
        for (int index = hex.length(); index < 4; index++) {
            builder.append('0');
        }
        builder.append(hex);
    }

    private static String asText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String fallback(String value, String fallback) {
        return value.length() == 0 ? fallback : value;
    }

    private static String cleanText(String value) {
        return value == null ? "" : value.trim();
    }

    private static String digitsOnly(String value) {
        String text = cleanText(value);
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            if (current >= '0' && current <= '9') {
                builder.append(current);
            }
        }
        return builder.toString();
    }

    private static final class JsonParser {
        private final String text;
        private int index = 0;

        JsonParser(String text) {
            this.text = text;
        }

        Object parseValueAndEnd() {
            Object value = parseValue();
            skipWhitespace();
            if (index != text.length()) {
                throw error("Trailing data after JSON value.");
            }
            return value;
        }

        private Object parseValue() {
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
                return null;
            }
            if (current == '-' || isDigit(current)) {
                return parseNumberText();
            }

            throw error("Unsupported JSON value.");
        }

        private Map<String, Object> parseObject() {
            expect('{');
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            skipWhitespace();
            if (peek('}')) {
                index++;
                return map;
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
                    return map;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() {
            expect('[');
            List<Object> values = new ArrayList<Object>();
            skipWhitespace();
            if (peek(']')) {
                index++;
                return values;
            }

            while (true) {
                values.add(parseValue());
                skipWhitespace();
                if (peek(']')) {
                    index++;
                    return values;
                }
                expect(',');
            }
        }

        private String parseString() {
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

        private char parseUnicodeEscape() {
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

        private String parseNumberText() {
            int start = index;
            if (peek('-')) {
                index++;
            }

            if (index >= text.length() || !isDigit(text.charAt(index))) {
                throw error("Bad JSON number.");
            }

            if (peek('0')) {
                index++;
            } else {
                while (index < text.length() && isDigit(text.charAt(index))) {
                    index++;
                }
            }

            if (peek('.')) {
                index++;
                if (index >= text.length() || !isDigit(text.charAt(index))) {
                    throw error("Bad JSON fraction.");
                }
                while (index < text.length() && isDigit(text.charAt(index))) {
                    index++;
                }
            }

            if (peek('e') || peek('E')) {
                index++;
                if (peek('+') || peek('-')) {
                    index++;
                }
                if (index >= text.length() || !isDigit(text.charAt(index))) {
                    throw error("Bad JSON exponent.");
                }
                while (index < text.length() && isDigit(text.charAt(index))) {
                    index++;
                }
            }

            return text.substring(start, index);
        }

        private boolean startsWith(String value) {
            return text.startsWith(value, index);
        }

        private boolean peek(char value) {
            return index < text.length() && text.charAt(index) == value;
        }

        private void expect(char value) {
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

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " Offset " + index + ".");
        }

        private boolean isDigit(char value) {
            return value >= '0' && value <= '9';
        }
    }
}
