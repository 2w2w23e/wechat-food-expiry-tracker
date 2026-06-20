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
            try {
                array.put(item.toJson());
            } catch (JSONException ignored) {
                // Invalid single records are skipped instead of corrupting the whole saved list.
            }
        }

        preferences.edit().putString(STORAGE_KEY, array.toString()).apply();
    }
}
