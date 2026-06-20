package com.shiqi.expirytracker;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class FoodStore {
    private static final String PREFS_NAME = "shiqi_android_v0";
    static final String STORAGE_KEY = "food_expiry_tracker_foods_v0";

    private final SharedPreferences preferences;

    FoodStore(Context context) {
        preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    List<FoodItem> loadFoods() {
        String raw = preferences.getString(STORAGE_KEY, null);
        List<FoodItem> foods = new ArrayList<FoodItem>();

        if (raw == null || raw.trim().length() == 0) {
            return foods;
        }

        try {
            JSONArray array = new JSONArray(raw);
            for (int index = 0; index < array.length(); index++) {
                JSONObject object = array.optJSONObject(index);
                if (object != null) {
                    FoodItem item = FoodItem.fromJson(object);
                    normalizeLoadedItem(item);
                    if (item.id.length() > 0 && item.name.length() > 0) {
                        foods.add(item);
                    }
                }
            }
        } catch (JSONException ignored) {
            return new ArrayList<FoodItem>();
        }

        return foods;
    }

    void saveFoods(List<FoodItem> foods) {
        JSONArray array = new JSONArray();

        for (FoodItem item : foods) {
            if (item == null) {
                continue;
            }
            try {
                array.put(item.toJson());
            } catch (JSONException ignored) {
                // Invalid single records are skipped instead of corrupting the whole saved list.
            }
        }

        preferences.edit().putString(STORAGE_KEY, array.toString()).commit();
    }

    private void normalizeLoadedItem(FoodItem item) {
        if (item.quantity < 0 || Double.isNaN(item.quantity) || Double.isInfinite(item.quantity)) {
            item.quantity = 1;
        }

        if (item.remainingQuantity < 0
                || Double.isNaN(item.remainingQuantity)
                || Double.isInfinite(item.remainingQuantity)) {
            item.remainingQuantity = item.quantity;
        }

        if (item.remainingQuantity > item.quantity) {
            item.remainingQuantity = item.quantity;
        }

        if (item.unit.length() == 0 || "piece".equals(item.unit)) {
            item.unit = "件";
        }

        if (item.storageMethod.length() == 0) {
            item.storageMethod = "room_temp";
        }
    }
}
