package com.shiqi.expirytracker;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class FoodItem {
    String id = "";
    String name = "";
    String category = "other";
    String productionDate = "";
    Integer shelfLifeValue = null;
    String shelfLifeUnit = "";
    String openedDate = "";
    Integer afterOpenShelfLifeValue = null;
    String afterOpenShelfLifeUnit = "";
    String expiryDate = "";
    String dateSource = "unknown";
    double quantity = 1;
    double remainingQuantity = 1;
    String unit = "件";
    String storageMethod = "room_temp";
    String location = FoodData.LOCATION_UNSPECIFIED;
    String notes = "";
    String createdAt = "";
    String updatedAt = "";
    boolean isFinished = false;
    String finishedAt = "";
    final List<Integer> smartReminderOffsets = new ArrayList<Integer>();
    String smartReminderFingerprint = "";
    int smartReminderPlannedDaysLeft = Integer.MIN_VALUE;
    String smartReminderPlannedOn = "";

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
        item.openedDate = optCleanString(json, "openedDate");
        item.afterOpenShelfLifeValue = optPositiveInteger(json, "afterOpenShelfLifeValue");
        item.afterOpenShelfLifeUnit = optCleanString(json, "afterOpenShelfLifeUnit");
        item.expiryDate = optCleanString(json, "expiryDate");
        item.dateSource = optCleanString(json, "dateSource");
        item.quantity = json.optDouble("quantity", 1);
        item.remainingQuantity = Math.min(json.optDouble("remainingQuantity", item.quantity), item.quantity);
        item.unit = optFallback(json.optString("unit", "件"), "件");
        item.storageMethod = optFallback(json.optString("storageMethod", "room_temp"), "room_temp");
        item.location = FoodData.normalizeLocationValue(json.optString("location", FoodData.LOCATION_UNSPECIFIED));
        item.notes = optCleanString(json, "notes");
        item.createdAt = optCleanString(json, "createdAt");
        item.updatedAt = optCleanString(json, "updatedAt");
        item.isFinished = json.optBoolean("isFinished", false);
        item.finishedAt = optCleanString(json, "finishedAt");
        JSONArray reminderOffsets = json.optJSONArray("smartReminderOffsets");
        if (reminderOffsets != null) {
            for (int index = 0; index < reminderOffsets.length(); index++) {
                int value = reminderOffsets.optInt(index, -1);
                if (value >= 0 && !item.smartReminderOffsets.contains(Integer.valueOf(value))) {
                    item.smartReminderOffsets.add(Integer.valueOf(value));
                }
            }
        }
        item.smartReminderFingerprint = optCleanString(json, "smartReminderFingerprint");
        item.smartReminderPlannedDaysLeft = json.has("smartReminderPlannedDaysLeft")
                ? json.optInt("smartReminderPlannedDaysLeft", Integer.MIN_VALUE)
                : Integer.MIN_VALUE;
        item.smartReminderPlannedOn = optCleanString(json, "smartReminderPlannedOn");
        item.normalizeQuantityBounds();

        if (!DateRules.isValidDateString(item.expiryDate)) {
            item.expiryDate = "";
            if (!"none".equals(item.dateSource)) {
                item.dateSource = "unknown";
            }
        }

        if (!DateRules.isValidDateString(item.openedDate)) {
            item.openedDate = "";
        }

        if (!DateRules.isShelfLifeUnit(item.afterOpenShelfLifeUnit)) {
            item.afterOpenShelfLifeValue = null;
            item.afterOpenShelfLifeUnit = "";
        }

        if (item.afterOpenShelfLifeValue == null) {
            item.afterOpenShelfLifeUnit = "";
        }

        if (!"calculated".equals(item.dateSource) && !"manual".equals(item.dateSource) && !"none".equals(item.dateSource)) {
            item.dateSource = item.expiryDate.length() > 0 ? "manual" : "unknown";
        }

        if ("none".equals(item.dateSource)) {
            item.expiryDate = "";
            item.shelfLifeValue = null;
            item.shelfLifeUnit = "";
        }

        if ("calculated".equals(item.dateSource) && !item.hasValidCalculatedDateSource()) {
            // Preserve a valid historical expiry date instead of letting the edit form recalculate it.
            item.dateSource = "manual";
        }

        return item;
    }

    boolean hasValidCalculatedDateSource() {
        if (!"calculated".equals(dateSource)
                || !DateRules.isValidDateString(expiryDate)
                || !DateRules.isValidDateString(productionDate)
                || shelfLifeValue == null
                || shelfLifeValue.intValue() <= 0
                || !DateRules.isShelfLifeUnit(shelfLifeUnit)) {
            return false;
        }
        return expiryDate.equals(DateRules.addShelfLife(productionDate, shelfLifeValue, shelfLifeUnit));
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
        putNullableString(json, "openedDate", openedDate);
        if (afterOpenShelfLifeValue == null) {
            json.put("afterOpenShelfLifeValue", JSONObject.NULL);
        } else {
            json.put("afterOpenShelfLifeValue", afterOpenShelfLifeValue.intValue());
        }
        putNullableString(json, "afterOpenShelfLifeUnit", afterOpenShelfLifeUnit);
        putNullableString(json, "expiryDate", expiryDate);
        json.put("dateSource", dateSource);
        json.put("quantity", quantity);
        json.put("remainingQuantity", remainingQuantity);
        json.put("unit", unit);
        json.put("storageMethod", storageMethod);
        json.put("location", FoodData.normalizeLocationValue(location));
        json.put("notes", notes);
        json.put("createdAt", createdAt);
        json.put("updatedAt", updatedAt);
        json.put("isFinished", isFinished);
        putNullableString(json, "finishedAt", finishedAt);
        JSONArray reminderOffsets = new JSONArray();
        for (Integer offset : smartReminderOffsets) {
            if (offset != null && offset.intValue() >= 0) {
                reminderOffsets.put(offset.intValue());
            }
        }
        json.put("smartReminderOffsets", reminderOffsets);
        putNullableString(json, "smartReminderFingerprint", smartReminderFingerprint);
        if (smartReminderPlannedDaysLeft == Integer.MIN_VALUE) {
            json.put("smartReminderPlannedDaysLeft", JSONObject.NULL);
        } else {
            json.put("smartReminderPlannedDaysLeft", smartReminderPlannedDaysLeft);
        }
        putNullableString(json, "smartReminderPlannedOn", smartReminderPlannedOn);
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
        item.openedDate = openedDate;
        item.afterOpenShelfLifeValue = afterOpenShelfLifeValue;
        item.afterOpenShelfLifeUnit = afterOpenShelfLifeUnit;
        item.expiryDate = expiryDate;
        item.dateSource = dateSource;
        item.quantity = quantity;
        item.remainingQuantity = remainingQuantity;
        item.unit = unit;
        item.storageMethod = storageMethod;
        item.location = location;
        item.notes = notes;
        item.createdAt = createdAt;
        item.updatedAt = updatedAt;
        item.isFinished = isFinished;
        item.finishedAt = finishedAt;
        item.smartReminderOffsets.addAll(smartReminderOffsets);
        item.smartReminderFingerprint = smartReminderFingerprint;
        item.smartReminderPlannedDaysLeft = smartReminderPlannedDaysLeft;
        item.smartReminderPlannedOn = smartReminderPlannedOn;
        return item;
    }

    FoodItem copyAsNewRecord(String newId, String now) {
        FoodItem item = copy();
        item.id = cleanText(newId);
        item.createdAt = cleanText(now);
        item.updatedAt = cleanText(now);
        item.isFinished = false;
        item.finishedAt = "";
        item.smartReminderOffsets.clear();
        item.smartReminderFingerprint = "";
        item.smartReminderPlannedDaysLeft = Integer.MIN_VALUE;
        item.smartReminderPlannedOn = "";
        item.normalizeQuantityBounds();
        item.remainingQuantity = item.quantity;
        return item;
    }

    void normalizeQuantityBounds() {
        quantity = normalizedQuantity(quantity);
        remainingQuantity = clampedRemainingQuantity(remainingQuantity, quantity);
    }

    static double normalizedQuantity(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0) {
            return 0;
        }
        return value;
    }

    static double clampedRemainingQuantity(double remaining, double quantity) {
        double safeQuantity = normalizedQuantity(quantity);
        double safeRemaining = normalizedQuantity(remaining);
        return Math.min(safeRemaining, safeQuantity);
    }

    private static String optCleanString(JSONObject json, String key) {
        if (!json.has(key) || json.isNull(key)) {
            return "";
        }
        return cleanText(json.optString(key, ""));
    }

    private static Integer optPositiveInteger(JSONObject json, String key) {
        if (!json.has(key) || json.isNull(key)) {
            return null;
        }

        int value = json.optInt(key);
        return value > 0 ? Integer.valueOf(value) : null;
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
