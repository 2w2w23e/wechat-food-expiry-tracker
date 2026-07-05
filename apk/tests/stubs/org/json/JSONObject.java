package org.json;

import java.util.LinkedHashMap;
import java.util.Map;

public class JSONObject {
    public static final Object NULL = new Object();

    private final Map<String, Object> values = new LinkedHashMap<String, Object>();

    public JSONObject() {}

    JSONObject(Map<String, Object> values) {
        if (values != null) {
            this.values.putAll(values);
        }
    }

    public String optString(String key, String fallback) {
        Object value = values.get(key);
        return value == null || value == NULL ? fallback : String.valueOf(value);
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    public boolean isNull(String key) {
        Object value = values.get(key);
        return value == null || value == NULL;
    }

    public int optInt(String key) {
        return optInt(key, 0);
    }

    public int optInt(String key, int fallback) {
        Object value = values.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public double optDouble(String key, double fallback) {
        Object value = values.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public boolean optBoolean(String key, boolean fallback) {
        Object value = values.get(key);
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }

    public JSONArray optJSONArray(String key) {
        Object value = values.get(key);
        return value instanceof JSONArray ? (JSONArray) value : null;
    }

    public JSONObject put(String key, Object value) throws JSONException {
        values.put(key, value == null ? NULL : value);
        return this;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            JSONTokener.appendJsonString(builder, entry.getKey());
            builder.append(':');
            JSONTokener.appendJsonValue(builder, entry.getValue());
        }
        builder.append('}');
        return builder.toString();
    }
}
