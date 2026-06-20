package com.shiqi.expirytracker;

import org.json.JSONException;
import org.json.JSONObject;

final class FoodItem {
    String id = "";
    String name = "";
    String category = "other";
    String productionDate = "";
    Integer shelfLifeValue = null;
    String shelfLifeUnit = "";
    String expiryDate = "";
    String dateSource = "unknown";
    double quantity = 1;
    double remainingQuantity = 1;
    String unit = "piece";
    String storageMethod = "room_temp";
    String notes = "";
    String createdAt = "";
    String updatedAt = "";

    static FoodItem fromJson(JSONObject json) {
        FoodItem item = new FoodItem();
        item.id = json.optString("id", "");
        item.name = json.optString("name", "");
        item.category = FoodData.normalizeCategoryValue(json.optString("category", "other"));
        item.productionDate = optCleanString(json, "productionDate");
        item.shelfLifeValue = json.has("shelfLifeValue") && !json.isNull("shelfLifeValue")
                ? Integer.valueOf(json.optInt("shelfLifeValue"))
                : null;
        item.shelfLifeUnit = optCleanString(json, "shelfLifeUnit");
        item.expiryDate = optCleanString(json, "expiryDate");
        item.dateSource = optCleanString(json, "dateSource");
        item.quantity = json.optDouble("quantity", 1);
        item.remainingQuantity = Math.min(json.optDouble("remainingQuantity", item.quantity), item.quantity);
        item.unit = optFallback(json.optString("unit", "piece"), "piece");
        item.storageMethod = optFallback(json.optString("storageMethod", "room_temp"), "room_temp");
        item.notes = optCleanString(json, "notes");
        item.createdAt = optCleanString(json, "createdAt");
        item.updatedAt = optCleanString(json, "updatedAt");

        if (!DateRules.isValidDateString(item.expiryDate)) {
            item.expiryDate = "";
            item.dateSource = "unknown";
        }

        if (!"calculated".equals(item.dateSource) && !"manual".equals(item.dateSource)) {
            item.dateSource = item.expiryDate.length() > 0 ? "manual" : "unknown";
        }

        return item;
    }

    JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("name", name);
        json.put("category", category);
        putNullableString(json, "productionDate", productionDate);
        if (shelfLifeValue == null) {
            json.put("shelfLifeValue", JSONObject.NULL);
        } else {
            json.put("shelfLifeValue", shelfLifeValue.intValue());
        }
        putNullableString(json, "shelfLifeUnit", shelfLifeUnit);
        putNullableString(json, "expiryDate", expiryDate);
        json.put("dateSource", dateSource);
        json.put("quantity", quantity);
        json.put("remainingQuantity", remainingQuantity);
        json.put("unit", unit);
        json.put("storageMethod", storageMethod);
        json.put("notes", notes);
        json.put("createdAt", createdAt);
        json.put("updatedAt", updatedAt);
        return json;
    }

    FoodItem copy() {
        FoodItem item = new FoodItem();
        item.id = id;
        item.name = name;
        item.category = category;
        item.productionDate = productionDate;
        item.shelfLifeValue = shelfLifeValue;
        item.shelfLifeUnit = shelfLifeUnit;
        item.expiryDate = expiryDate;
        item.dateSource = dateSource;
        item.quantity = quantity;
        item.remainingQuantity = remainingQuantity;
        item.unit = unit;
        item.storageMethod = storageMethod;
        item.notes = notes;
        item.createdAt = createdAt;
        item.updatedAt = updatedAt;
        return item;
    }

    private static String optCleanString(JSONObject json, String key) {
        if (!json.has(key) || json.isNull(key)) {
            return "";
        }
        return cleanText(json.optString(key, ""));
    }

    static String cleanText(String value) {
        if (value == null) {
            return "";
        }

        String text = value.trim();
        return "未填写".equals(text) ? "" : text;
    }

    private static String optFallback(String value, String fallback) {
        String text = cleanText(value);
        return text.length() > 0 ? text : fallback;
    }

    private static void putNullableString(JSONObject json, String key, String value) throws JSONException {
        String text = cleanText(value);
        if (text.length() == 0) {
            json.put(key, JSONObject.NULL);
        } else {
            json.put(key, text);
        }
    }
}
