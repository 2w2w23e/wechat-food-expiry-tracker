package org.json;

import java.util.ArrayList;
import java.util.List;

public class JSONArray {
    private final List<Object> values = new ArrayList<Object>();

    public JSONArray() {}

    JSONArray(List<Object> values) {
        if (values != null) {
            this.values.addAll(values);
        }
    }

    public int length() {
        return values.size();
    }

    public JSONObject optJSONObject(int index) {
        if (index < 0 || index >= values.size()) {
            return null;
        }
        Object value = values.get(index);
        return value instanceof JSONObject ? (JSONObject) value : null;
    }

    public int optInt(int index, int fallback) {
        if (index < 0 || index >= values.size()) {
            return fallback;
        }
        Object value = values.get(index);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public JSONArray put(Object value) {
        values.add(value == null ? JSONObject.NULL : value);
        return this;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                builder.append(',');
            }
            JSONTokener.appendJsonValue(builder, values.get(index));
        }
        builder.append(']');
        return builder.toString();
    }
}
